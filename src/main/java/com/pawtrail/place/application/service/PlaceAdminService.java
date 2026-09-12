package com.pawtrail.place.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.message.outbox.OutboxEventRecorder;
import com.pawtrail.place.application.dto.input.PlaceAdminUpdateInput;
import com.pawtrail.place.application.dto.output.PlaceDetailOutput;
import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.event.payload.PlaceUpdatedEvent;
import com.pawtrail.place.domain.exception.PlaceErrorCode;
import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.model.PlaceSourceDetach;
import com.pawtrail.place.domain.model.PlaceSourceLink;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.domain.repository.PlaceSourceDetachRepository;
import com.pawtrail.place.domain.repository.PlaceSourceLinkRepository;
import com.pawtrail.place.domain.rule.AddressNormalizer;
import com.pawtrail.place.domain.rule.PlaceMerger;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 장소를 직접 고치는 서비스입니다.
 *
 * 적재와 나눠 둔 것은 값을 다루는 방식이 반대이기 때문입니다.
 * 적재는 빈 칸만 채우고 이미 있는 값은 건드리지 않습니다.
 * 여기는 사람이 판단한 값으로 덮어씁니다.
 *
 * 이 서비스가 생기기 전까지 place 의 값을 바꿀 수 있는 자리가 거의 없었습니다.
 * 이름은 바꾸는 메서드조차 없어 장소가 생길 때의 값으로 고정돼 있었습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceAdminService {

    private final PlaceRepository placeRepository;
    private final PlaceSourceLinkRepository sourceLinkRepository;
    private final PlaceSourceDetachRepository sourceDetachRepository;
    private final PlaceQueryService placeQueryService;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 장소를 고치고 잠급니다.
     *
     * 보낸 것만 바꿉니다. 플래그가 거짓인 칸은 손대지 않습니다.
     *
     * 고치면 admin_locked 이 켜집니다.
     * 이 뒤로 수집 배치는 이 행을 고치지 않고 발견한 값을 대기 목록에 쌓습니다.
     * 잠그지 않으면 관리자가 고친 값을 다음 수집이 조용히 덮습니다.
     *
     * 잠금은 행 단위입니다.
     * 전화번호 하나만 고쳐도 그 장소의 모든 칸이 잠기는데 명세가 boolean 하나로 못 박았습니다.
     *
     * place.updated 를 발행합니다. search 가 색인을 다시 만듭니다.
     *
     * 응답은 상세 조회와 같은 형태입니다.
     * 관리자 화면이 고친 뒤 그 자리에서 결과를 보여줄 수 있고,
     * 고친 값과 파생값이 실제로 어떻게 됐는지가 그대로 드러납니다.
     */
    @Transactional
    public PlaceDetailOutput update(UUID placeId, PlaceAdminUpdateInput input) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));

        // 이름과 주소는 파생값을 함께 다시 만듦
        // 엔티티 안에서 하므로 여기서 정규화를 부를 일이 없음
        if (input.name() != null) {
            place.renameByAdmin(input.name());
        }
        if (input.addressProvided()) {
            requireNormalizable(input.addressRoad(), input.addressJibun());
            place.changeAddressByAdmin(input.addressRoad(), input.addressJibun());
        }

        if (input.telProvided()) {
            place.changeTelByAdmin(input.tel());
        }
        if (input.homepageProvided()) {
            place.changeHomepageByAdmin(input.homepage());
        }
        if (input.reservationUrlProvided()) {
            place.changeReservationUrlByAdmin(input.reservationUrl());
        }
        if (input.imageUrlProvided()) {
            place.changeImageUrlByAdmin(input.imageUrl());
        }
        if (input.overviewProvided()) {
            place.changeOverviewByAdmin(input.overview());
        }
        if (input.businessHoursProvided()) {
            place.changeBusinessHoursByAdmin(input.businessHours());
        }
        if (input.closedDaysProvided()) {
            place.changeClosedDaysByAdmin(input.closedDays());
        }
        if (input.status() != null) {
            place.changeStatus(input.status());
        }

        place.lockByAdmin();
        placeRepository.save(place);
        outboxEventRecorder.record(new PlaceUpdatedEvent(placeId));

        log.info("관리자가 장소를 고쳤습니다: placeId={}", placeId);
        return placeQueryService.getDetail(placeId);
    }

    /**
     * 정규화할 수 없는 주소를 미리 막습니다.
     *
     * 엔티티도 같은 것을 막습니다. 거기는 마지막 방어선이라 IllegalArgumentException 이고,
     * 그대로 두면 공통 폴백이 잡아 500 이 나갑니다.
     * 사용자에게 보이는 실패는 여기서 도메인 에러 코드로 내보냅니다.
     *
     * 정규화를 두 번 계산하게 됩니다.
     * 관리자 API 는 한 번에 한 건이라 비용이 없고,
     * 엔티티가 파생값을 스스로 만들어 부르는 쪽이 빠뜨릴 수 없게 한 구조를 지키는 편이 낫습니다.
     */
    private void requireNormalizable(String addressRoad, String addressJibun) {
        if (AddressNormalizer.normalize(addressRoad, addressJibun, null) == null) {
            throw new CustomException(PlaceErrorCode.PLACE_ADDRESS_INVALID);
        }
    }

    /**
     * 잘못 묶인 소스를 떼어냅니다.
     *
     * 연결 행을 실제로 지웁니다.
     * 소프트로 두면 uq_place_source 에 걸려 그 소스를 어디에도 다시 붙일 수 없게 됩니다.
     *
     * place 본체 값은 손대지 않습니다.
     * 떼어낸 소스가 채워 넣은 칸이 그대로 남는데, 어느 칸을 누가 채웠는지 place 가 모릅니다.
     * 연결 표에 원본 값 컬럼이 없어 남은 소스로 다시 병합할 수도 없습니다.
     * 이상한 값이 남으면 관리자가 같은 화면에서 PATCH 로 이어서 고칩니다.
     *
     * 분리 기록을 남깁니다. 이것이 없으면 다음 적재에 그대로 되붙습니다.
     * 연결 행이 사라진 소스 레코드는 처음 보는 것이 되어 매처를 다시 타고,
     * 주소가 그대로라 같은 장소로 돌아옵니다.
     *
     * 소스가 하나뿐이면 거부합니다.
     * 이 표가 "지금 이 장소가 어느 소스로 이뤄져 있나" 를 담으므로 0 개가 되면 답이 사라집니다.
     *
     * 한 트랜잭션입니다.
     * 연결만 지우고 기록이 안 남으면 다음 적재에 되붙어 분리가 없던 일이 됩니다.
     */
    @Transactional
    public void detachSource(UUID placeId, UUID sourceLinkId, String adminId) {
        if (placeRepository.findById(placeId).isEmpty()) {
            throw new CustomException(PlaceErrorCode.PLACE_NOT_FOUND);
        }

        PlaceSourceLink target = sourceLinkRepository.findById(sourceLinkId)
                .filter(link -> link.getPlaceId().equals(placeId))
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_SOURCE_NOT_FOUND));

        List<PlaceSourceLink> links = sourceLinkRepository.findAllByPlaceId(placeId);
        if (links.size() <= 1) {
            throw new CustomException(PlaceErrorCode.PLACE_LAST_SOURCE);
        }

        sourceDetachRepository.save(PlaceSourceDetach.of(
                placeId, target.getSource(), target.getSourceId(), adminId));
        // 지우기를 먼저 내보냄
        // 하이버네이트가 UPDATE 를 DELETE 보다 먼저 실행하므로
        // 그냥 두면 승격 UPDATE 가 먼저 나가 대표가 잠깐 둘이 되고 부분 UNIQUE 에 걸림
        sourceLinkRepository.deleteAndFlush(target);

        if (target.isPrimary()) {
            promoteNextPrimary(placeId, links, sourceLinkId);
        }

        outboxEventRecorder.record(new PlaceUpdatedEvent(placeId));
        log.info("관리자가 소스를 뗐습니다: placeId={}, source={}, sourceId={}",
                placeId, target.getSource(), target.getSourceId());
    }

    /**
     * 대표가 사라졌을 때 남은 것 중 하나를 올립니다.
     *
     * 고르는 기준은 적재의 대표 순서와 같습니다.
     * 다른 기준을 쓰면 같은 장소가 적재로 정해졌을 때와 분리 뒤가 달라집니다.
     *
     * 기존 대표를 먼저 내리지 않아도 됩니다.
     * 그 행은 바로 앞에서 지워져 데이터베이스로 내보내졌으므로 부분 UNIQUE 가 비어 있습니다.
     */
    private void promoteNextPrimary(UUID placeId, List<PlaceSourceLink> links, UUID removedId) {
        List<PlaceSourceLink> remaining = links.stream()
                .filter(link -> !link.getId().equals(removedId))
                .toList();

        SourceType next = PlaceMerger.choosePrimary(
                remaining.stream().map(PlaceSourceLink::getSource).toList());
        if (next == null) {
            return;
        }

        // 같은 소스가 여럿 붙어 있을 수 있어 먼저 나온 것을 올림
        // 같은 소스 안의 중복도 병합하기로 해 연결 행이 둘 남는 장소가 실제로 있음
        remaining.stream()
                .filter(link -> link.getSource() == next)
                .findFirst()
                .ifPresent(link -> {
                    link.promote();
                    sourceLinkRepository.save(link);
                    log.info("대표 소스를 승격했습니다: placeId={}, source={}", placeId, next);
                });
    }
}
