package com.pawtrail.place.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.place.application.dto.input.PlaceAdminUpdateInput;
import com.pawtrail.place.application.dto.output.PlacePendingOutput;
import com.pawtrail.place.domain.enums.PendingStatus;
import com.pawtrail.place.domain.exception.PlaceErrorCode;
import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import com.pawtrail.place.domain.repository.PlacePendingUpdateRepository;
import com.pawtrail.place.domain.repository.PlaceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 반영 대기 값을 보고 승인하거나 반려하는 서비스입니다.
 *
 * 이름이 비슷한 PlacePendingUpdateService 와 하는 일이 다릅니다.
 * 그쪽은 수집이 대기 행을 만드는 자리이고 별도 트랜잭션으로 돌아갑니다.
 * 여기는 관리자가 그 행을 처리하는 자리입니다.
 *
 * 값을 직접 쓰지 않고 PlaceAdminService 를 부릅니다.
 * 그 안에 검증과 파생값 재계산과 잠금과 place.updated 발행이 전부 들어 있습니다.
 * 직접 쓰면 그 규칙을 두 곳에서 관리하게 되고 한쪽만 고치는 날이 옵니다.
 * 승인의 뜻이 "관리자가 이 값으로 고친다" 이므로 손으로 고치는 것과 결과가 같아야 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlacePendingAdminService {

    // 주소 두 컬럼임
    //
    // 둘이 한 덩어리라 따로 반영할 수 없어 이름을 상수로 둠
    // 문자열을 여기저기 쓰면 오타가 조용히 "그 밖의 필드" 로 흘러감
    private static final String ADDRESS_ROAD = "address_road";
    private static final String ADDRESS_JIBUN = "address_jibun";

    private final PlacePendingUpdateRepository pendingUpdateRepository;
    private final PlaceRepository placeRepository;
    private final PlaceAdminService placeAdminService;

    /**
     * 처리하지 않은 대기 값을 최신순으로 돌려줍니다.
     *
     * 장소 이름은 place 에서 한 번에 가져옵니다.
     * 줄마다 조회하면 한 쪽에 스무 번이 나갑니다.
     */
    @Transactional(readOnly = true)
    public Page<PlacePendingOutput> list(Pageable pageable) {
        Page<PlacePendingUpdate> page = pendingUpdateRepository.findPending(pageable);
        if (page.isEmpty()) {
            return page.map(pending -> PlacePendingOutput.of(pending, null));
        }

        List<UUID> placeIds = page.getContent().stream()
                .map(PlacePendingUpdate::getPlaceId)
                .distinct()
                .toList();
        Map<UUID, String> names = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Place::getName));

        // 이름이 없으면 null 로 둠
        //
        // 장소가 사라질 일은 없으나 대기 행이 남아 있는 동안 못 찾는 경우를 대비함
        // 여기서 예외를 내면 한 줄 때문에 목록 전체가 실패함
        return page.map(pending ->
                PlacePendingOutput.of(pending, names.get(pending.getPlaceId())));
    }

    /**
     * 승인합니다. 그 값을 place 에 반영하고 대기 행을 처리됨으로 표시합니다.
     *
     * 주소는 도로명과 지번을 묶어 함께 반영합니다.
     * 둘이 한 덩어리라 한쪽만 넘기면 나머지가 지워지거나 반쪽 주소에서 정규화 값이 나옵니다.
     * 짝이 되는 대기 값이 있으면 그것도 함께 승인하고, 없으면 place 의 지금 값을 씁니다.
     * 없다는 것은 그 소스가 그쪽을 바꾸지 않았다는 뜻입니다.
     * 수집이 값이 달라졌을 때만 대기 행을 만들기 때문입니다.
     */
    @Transactional
    public void approve(UUID pendingId, String adminId) {
        PlacePendingUpdate pending = getPending(pendingId);

        Optional<PlacePendingUpdate> companion = findAddressCompanion(pending);
        placeAdminService.update(pending.getPlaceId(), toInput(pending, companion));

        pending.approve(adminId);
        pendingUpdateRepository.save(pending);
        companion.ifPresent(other -> {
            other.approve(adminId);
            pendingUpdateRepository.save(other);
        });

        log.info("관리자가 반영 대기 값을 승인했습니다: pendingId={}, placeId={}, field={}",
                pendingId, pending.getPlaceId(), pending.getFieldName());
    }

    /**
     * 반려합니다. place 는 그대로 둡니다.
     *
     * 짝이 되는 주소 대기 값은 건드리지 않습니다.
     * 반려는 필드 단위 판단이라 도로명은 틀렸고 지번은 맞을 수 있습니다.
     *
     * 반려한 값은 다음 수집에서 다시 쌓이지 않습니다.
     * 수집이 대기 행을 만들기 전에 이 기록을 봅니다.
     */
    @Transactional
    public void reject(UUID pendingId, String adminId) {
        PlacePendingUpdate pending = getPending(pendingId);

        pending.reject(adminId);
        pendingUpdateRepository.save(pending);

        log.info("관리자가 반영 대기 값을 반려했습니다: pendingId={}, placeId={}, field={}",
                pendingId, pending.getPlaceId(), pending.getFieldName());
    }

    /**
     * 아직 처리하지 않은 대기 값을 찾습니다.
     *
     * 이미 처리한 것이면 409 입니다.
     * 엔티티도 같은 것을 막으나 거기는 마지막 방어선이라 IllegalStateException 이고,
     * 그대로 두면 공통 폴백이 잡아 500 이 나갑니다.
     */
    private PlacePendingUpdate getPending(UUID pendingId) {
        PlacePendingUpdate pending = pendingUpdateRepository.findById(pendingId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PENDING_NOT_FOUND));
        if (pending.getStatus() != PendingStatus.PENDING) {
            throw new CustomException(PlaceErrorCode.PENDING_ALREADY_RESOLVED);
        }
        return pending;
    }

    /**
     * 주소 대기 값이면 짝이 되는 쪽을 찾습니다.
     *
     * 주소가 아니면 빈 값입니다.
     */
    private Optional<PlacePendingUpdate> findAddressCompanion(PlacePendingUpdate pending) {
        String field = pending.getFieldName();
        if (!isAddress(field)) {
            return Optional.empty();
        }
        String other = ADDRESS_ROAD.equals(field) ? ADDRESS_JIBUN : ADDRESS_ROAD;
        return pendingUpdateRepository
                .findPendingByPlaceIdAndFieldName(pending.getPlaceId(), other);
    }

    /**
     * 대기 값을 관리자 수정 입력으로 바꿉니다.
     *
     * 필드 하나만 세우고 나머지는 플래그를 내려 둡니다.
     * 그래야 PlaceAdminService 가 그 칸만 고치고 나머지를 건드리지 않습니다.
     *
     * 알 수 없는 필드 이름이면 예외입니다.
     * 이 값은 사용자 요청이 아니라 우리가 recordPending 에 적어 둔 목록에서 오므로,
     * 여기 걸린다면 그쪽을 늘리고 이 매핑을 빠뜨린 것입니다.
     */
    private PlaceAdminUpdateInput toInput(PlacePendingUpdate pending,
                                          Optional<PlacePendingUpdate> companion) {
        String field = pending.getFieldName();
        String value = pending.getNewValue();

        if (isAddress(field)) {
            return addressInput(pending, companion);
        }

        return switch (field) {
            case "name" -> input(builder -> builder.name(value));
            case "tel" -> input(builder -> builder.tel(value));
            case "homepage" -> input(builder -> builder.homepage(value));
            case "reservation_url" -> input(builder -> builder.reservationUrl(value));
            case "image_url" -> input(builder -> builder.imageUrl(value));
            case "business_hours" -> input(builder -> builder.businessHours(value));
            default -> throw new IllegalStateException(
                    "반영할 수 없는 필드입니다: " + field);
        };
    }

    /**
     * 주소 두 값을 한 덩어리로 세웁니다.
     *
     * 승인하는 쪽은 새 값이고, 반대쪽은 짝이 되는 대기 값이 있으면 그 값,
     * 없으면 place 의 지금 값입니다.
     */
    private PlaceAdminUpdateInput addressInput(PlacePendingUpdate pending,
                                               Optional<PlacePendingUpdate> companion) {
        Place place = placeRepository.findById(pending.getPlaceId())
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));

        String companionValue = companion.map(PlacePendingUpdate::getNewValue).orElse(null);
        boolean isRoad = ADDRESS_ROAD.equals(pending.getFieldName());

        String road = isRoad
                ? pending.getNewValue()
                : orElse(companionValue, companion.isPresent(), place.getAddressRoad());
        String jibun = isRoad
                ? orElse(companionValue, companion.isPresent(), place.getAddressJibun())
                : pending.getNewValue();

        return input(builder -> builder.address(road, jibun));
    }

    // 짝이 있으면 그 값을, 없으면 지금 값을 씀
    //
    // 짝의 값이 null 인 경우와 짝이 없는 경우를 갈라야 함
    // 앞은 "그 소스가 지웠다" 이고 뒤는 "그 소스가 안 바꿨다" 라 뜻이 다름
    private static String orElse(String companionValue, boolean hasCompanion, String current) {
        return hasCompanion ? companionValue : current;
    }

    private static boolean isAddress(String field) {
        return ADDRESS_ROAD.equals(field) || ADDRESS_JIBUN.equals(field);
    }

    private static PlaceAdminUpdateInput input(Consumer<Builder> setup) {
        Builder builder = new Builder();
        setup.accept(builder);
        return builder.build();
    }

    /**
     * 한 칸만 세운 입력을 만듭니다.
     *
     * 입력의 인자가 열아홉이라 그대로 적으면 어느 칸을 세웠는지가 묻힙니다.
     */
    private static final class Builder {

        private String name;
        private boolean addressProvided;
        private String addressRoad;
        private String addressJibun;
        private boolean telProvided;
        private String tel;
        private boolean homepageProvided;
        private String homepage;
        private boolean reservationUrlProvided;
        private String reservationUrl;
        private boolean imageUrlProvided;
        private String imageUrl;
        private boolean businessHoursProvided;
        private String businessHours;

        void name(String value) {
            this.name = value;
        }

        void address(String road, String jibun) {
            this.addressProvided = true;
            this.addressRoad = road;
            this.addressJibun = jibun;
        }

        void tel(String value) {
            this.telProvided = true;
            this.tel = value;
        }

        void homepage(String value) {
            this.homepageProvided = true;
            this.homepage = value;
        }

        void reservationUrl(String value) {
            this.reservationUrlProvided = true;
            this.reservationUrl = value;
        }

        void imageUrl(String value) {
            this.imageUrlProvided = true;
            this.imageUrl = value;
        }

        void businessHours(String value) {
            this.businessHoursProvided = true;
            this.businessHours = value;
        }

        PlaceAdminUpdateInput build() {
            return new PlaceAdminUpdateInput(
                    name,
                    addressProvided, addressRoad, addressJibun,
                    telProvided, tel,
                    homepageProvided, homepage,
                    reservationUrlProvided, reservationUrl,
                    imageUrlProvided, imageUrl,
                    false, null,
                    businessHoursProvided, businessHours,
                    false, null,
                    null);
        }
    }
}
