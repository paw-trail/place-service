package com.pawtrail.place.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.message.outbox.OutboxEventRecorder;
import com.pawtrail.place.application.dto.input.PlaceAdminUpdateInput;
import com.pawtrail.place.domain.enums.MatchMethod;
import com.pawtrail.place.domain.enums.PlaceStatus;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.enums.TelSource;
import com.pawtrail.place.domain.event.payload.PlaceUpdatedEvent;
import com.pawtrail.place.domain.exception.PlaceErrorCode;
import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.model.PlaceSourceDetach;
import com.pawtrail.place.domain.model.PlaceSourceLink;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.domain.repository.PlaceSourceDetachRepository;
import com.pawtrail.place.domain.repository.PlaceSourceLinkRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자가 장소를 고치고 소스를 떼는 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 띄우지 않습니다.
 * 여기서 지키려는 것은 어느 칸을 바꾸고 어느 칸을 두는가이며
 * 그 판단에 데이터베이스가 관여하지 않습니다.
 *
 * 특히 지키려는 것이 셋입니다.
 * 보낸 것만 바꾸지 않으면 전화번호를 고치려던 요청이 소개문을 지웁니다.
 * 이름과 주소를 고칠 때 파생값을 함께 만들지 않으면 그 장소만 다음 병합에서 이상해지고
 * 오류가 나지 않아 알아채기 어렵습니다.
 * 분리 기록이 안 남으면 다음 적재에 그대로 되붙어 분리가 없던 일이 됩니다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceAdminServiceTest {

    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID LINK_1 = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID LINK_2 = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID MISSING = UUID.fromString("cccccccc-0000-7000-8000-000000000003");
    private static final String ADMIN = "admin-account-id";

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceSourceLinkRepository sourceLinkRepository;

    @Mock
    private PlaceSourceDetachRepository sourceDetachRepository;

    @Mock
    private PlaceQueryService placeQueryService;

    @Mock
    private OutboxEventRecorder outboxEventRecorder;

    @InjectMocks
    private PlaceAdminService placeAdminService;

    // ── 장소 수정 ─────────────────────────────────────────────

    @Test
    @DisplayName("보낸 칸만 바꾸고 나머지는 그대로 둔다")
    void 보낸_것만_바꾼다() {
        Place place = filled();
        givenPlace(place);

        placeAdminService.update(PLACE_A, only(builder -> builder.tel("031-000-0000")));

        assertThat(place.getTel()).isEqualTo("031-000-0000");
        assertThat(place.getOverview()).isEqualTo("소개문");
        assertThat(place.getHomepage()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("null 을 보내면 그 칸을 지운다")
    void null_이면_지운다() {
        Place place = filled();
        givenPlace(place);

        placeAdminService.update(PLACE_A, only(builder -> builder.overview(null)));

        assertThat(place.getOverview()).isNull();
    }

    @Test
    @DisplayName("전화번호를 지우면 출처도 함께 지운다")
    void 전화번호와_출처는_짝() {
        Place place = filled();
        givenPlace(place);

        placeAdminService.update(PLACE_A, only(builder -> builder.tel(null)));

        assertThat(place.getTel()).isNull();
        assertThat(place.getTelSource()).isNull();
    }

    @Test
    @DisplayName("이름을 고치면 정규화 값과 별칭을 다시 만든다")
    void 이름의_파생값() {
        Place place = filled();
        place.applyNormalized("송파나루공원", List.of("석촌호수"), "서울|송파구도로1");
        givenPlace(place);

        placeAdminService.update(PLACE_A, only(builder -> builder.name("석촌호수공원")));

        assertThat(place.getName()).isEqualTo("석촌호수공원");
        assertThat(place.getNameNormalized()).isEqualTo("석촌호수공원");
        assertThat(place.getNameAlias()).isEmpty();
    }

    @Test
    @DisplayName("주소를 고치면 정규화 주소와 시도 코드를 다시 만든다")
    void 주소의_파생값() {
        Place place = filled();
        givenPlace(place);

        placeAdminService.update(PLACE_A,
                only(builder -> builder.address("부산광역시 해운대구 해운대해변로 264", null)));

        assertThat(place.getAddressRoad()).isEqualTo("부산광역시 해운대구 해운대해변로 264");
        assertThat(place.getAddressNormalized()).startsWith("부산|");
        assertThat(place.getSidoCode()).isEqualTo("26");
    }

    @Test
    @DisplayName("주소를 둘 다 비우면 거부한다")
    void 주소는_비울_수_없다() {
        givenPlace(filled());

        // 요청 단계의 @AssertTrue 가 먼저 막으므로 여기까지 오지 않음
        // 그래도 검사하는 것은 이 경로가 마지막 방어선이기 때문임
        // 정규화가 null 을 돌려주므로 서비스의 선검증에 먼저 걸림
        assertThatThrownBy(() ->
                placeAdminService.update(PLACE_A, only(builder -> builder.address(null, null))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_ADDRESS_INVALID);
    }

    @Test
    @DisplayName("정규화할 수 없는 주소는 PLACE_ADDRESS_INVALID 다")
    void 정규화_실패는_거부() {
        givenPlace(filled());

        // 시도 표기가 없어 AddressNormalizer 가 답하지 못하는 주소임
        // 엔티티도 같은 것을 막으나 거기는 마지막 방어선이라 IllegalArgumentException 임
        // 사용자에게 보이는 실패는 서비스가 도메인 에러 코드로 내보내는지 봄
        assertThatThrownBy(() ->
                placeAdminService.update(PLACE_A, only(builder -> builder.address("남정면 양성리", null))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_ADDRESS_INVALID);
    }

    @Test
    @DisplayName("고치면 잠기고 place.updated 가 나간다")
    void 잠금과_이벤트() {
        Place place = filled();
        givenPlace(place);

        placeAdminService.update(PLACE_A, only(builder -> builder.status(PlaceStatus.CLOSED)));

        assertThat(place.getStatus()).isEqualTo(PlaceStatus.CLOSED);
        assertThat(place.isAdminLocked()).isTrue();
        verify(outboxEventRecorder).record(new PlaceUpdatedEvent(PLACE_A));
    }

    @Test
    @DisplayName("없는 장소는 PLACE_NOT_FOUND 다")
    void 없는_장소_수정() {
        when(placeRepository.findById(MISSING)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                placeAdminService.update(MISSING, only(builder -> builder.tel("02-000-0000"))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
    }

    // ── 소스 분리 ─────────────────────────────────────────────

    @Test
    @DisplayName("연결을 지우고 분리 기록을 남긴다")
    void 분리_기록() {
        PlaceSourceLink primary = link(LINK_1, SourceType.PET_TOUR, "126508", true);
        PlaceSourceLink other = link(LINK_2, SourceType.CULTURE_CSV, "A공원|서울 1", false);
        givenLinks(LINK_2, primary, other);

        placeAdminService.detachSource(PLACE_A, LINK_2, ADMIN);

        verify(sourceLinkRepository).deleteAndFlush(other);

        ArgumentCaptor<PlaceSourceDetach> captor = ArgumentCaptor.forClass(PlaceSourceDetach.class);
        verify(sourceDetachRepository).save(captor.capture());
        PlaceSourceDetach saved = captor.getValue();
        assertThat(saved.getPlaceId()).isEqualTo(PLACE_A);
        assertThat(saved.getSource()).isEqualTo(SourceType.CULTURE_CSV);
        assertThat(saved.getSourceId()).isEqualTo("A공원|서울 1");
        assertThat(saved.getDetachedBy()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("대표를 떼면 남은 것 중 대표 순서가 앞선 소스를 올린다")
    void 대표_승격() {
        PlaceSourceLink primary = link(LINK_1, SourceType.CULTURE_CSV, "A공원|서울 1", true);
        PlaceSourceLink other = link(LINK_2, SourceType.PET_TOUR, "126508", false);
        givenLinks(LINK_1, primary, other);

        placeAdminService.detachSource(PLACE_A, LINK_1, ADMIN);

        assertThat(other.isPrimary()).isTrue();
        verify(sourceLinkRepository).save(other);
    }

    @Test
    @DisplayName("대표가 아닌 것을 떼면 승격하지 않는다")
    void 승격_없음() {
        PlaceSourceLink primary = link(LINK_1, SourceType.PET_TOUR, "126508", true);
        PlaceSourceLink other = link(LINK_2, SourceType.CULTURE_CSV, "A공원|서울 1", false);
        givenLinks(LINK_2, primary, other);

        placeAdminService.detachSource(PLACE_A, LINK_2, ADMIN);

        assertThat(primary.isPrimary()).isTrue();
        verify(sourceLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("소스가 하나뿐이면 뗄 수 없다")
    void 마지막_소스() {
        PlaceSourceLink only = link(LINK_1, SourceType.PET_TOUR, "126508", true);
        givenLinks(LINK_1, only);

        assertThatThrownBy(() -> placeAdminService.detachSource(PLACE_A, LINK_1, ADMIN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_LAST_SOURCE);

        verify(sourceDetachRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 장소에 붙은 연결은 뗄 수 없다")
    void 남의_연결() {
        PlaceSourceLink foreign = link(LINK_1, SourceType.PET_TOUR, "126508", true);
        setField(foreign, "placeId", MISSING);
        when(placeRepository.findById(PLACE_A)).thenReturn(Optional.of(filled()));
        when(sourceLinkRepository.findById(LINK_1)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> placeAdminService.detachSource(PLACE_A, LINK_1, ADMIN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_SOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("떼면 place.updated 가 나간다")
    void 분리_이벤트() {
        givenLinks(LINK_2,
                link(LINK_1, SourceType.PET_TOUR, "126508", true),
                link(LINK_2, SourceType.CULTURE_CSV, "A공원|서울 1", false));

        placeAdminService.detachSource(PLACE_A, LINK_2, ADMIN);

        verify(outboxEventRecorder).record(new PlaceUpdatedEvent(PLACE_A));
    }

    // ── 준비 ─────────────────────────────────────────────────

    private void givenPlace(Place place) {
        when(placeRepository.findById(PLACE_A)).thenReturn(Optional.of(place));
    }

    /**
     * 그 장소에 소스가 붙어 있는 상태를 만듭니다.
     *
     * 뗄 대상만 findById 로 세웁니다.
     * 안 쓰는 것까지 세우면 Mockito 가 불필요한 스텁으로 보고 검사를 실패시킵니다.
     */
    private void givenLinks(UUID targetId, PlaceSourceLink... links) {
        when(placeRepository.findById(PLACE_A)).thenReturn(Optional.of(filled()));
        for (PlaceSourceLink link : links) {
            if (link.getId().equals(targetId)) {
                when(sourceLinkRepository.findById(targetId)).thenReturn(Optional.of(link));
            }
        }
        when(sourceLinkRepository.findAllByPlaceId(PLACE_A)).thenReturn(List.of(links));
    }

    /**
     * 값이 다 채워진 장소를 만듭니다.
     *
     * 보낸 것만 바꾼다는 규칙은 다른 칸에 값이 있어야 검사할 수 있습니다.
     */
    private static Place filled() {
        Place place = Place.create("A 공원", PlaceType.PARK,
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"));
        setField(place, "id", PLACE_A);
        place.applyAddress("서울특별시 송파구 도로 1", null, "11", null);
        place.applyContact("02-000-0000", TelSource.PET_TOUR, "https://example.com",
                "https://reserve.example.com", "https://img.example.com/a.jpg", "Type1");
        place.applyDescription("소개문", "10:00~18:00", "연중무휴");
        return place;
    }

    private static PlaceSourceLink link(UUID id, SourceType source, String sourceId, boolean primary) {
        PlaceSourceLink link = primary
                ? PlaceSourceLink.createPrimary(PLACE_A, source, sourceId)
                : PlaceSourceLink.link(PLACE_A, source, sourceId, MatchMethod.ADDRESS, null);
        setField(link, "id", id);
        return link;
    }

    /**
     * 요청 하나만 담은 입력을 만듭니다.
     *
     * 필드가 열아홉이라 테스트마다 전부 적으면 무엇을 검사하는지가 묻힙니다.
     * 고치려는 칸만 세우고 나머지는 플래그를 내려 둡니다.
     */
    private static PlaceAdminUpdateInput only(java.util.function.Consumer<Builder> setup) {
        Builder builder = new Builder();
        setup.accept(builder);
        return builder.build();
    }

    private static final class Builder {

        private String name;
        private boolean addressProvided;
        private String addressRoad;
        private String addressJibun;
        private boolean telProvided;
        private String tel;
        private boolean overviewProvided;
        private String overview;
        private PlaceStatus status;

        Builder name(String value) {
            this.name = value;
            return this;
        }

        Builder address(String road, String jibun) {
            this.addressProvided = true;
            this.addressRoad = road;
            this.addressJibun = jibun;
            return this;
        }

        Builder tel(String value) {
            this.telProvided = true;
            this.tel = value;
            return this;
        }

        Builder overview(String value) {
            this.overviewProvided = true;
            this.overview = value;
            return this;
        }

        Builder status(PlaceStatus value) {
            this.status = value;
            return this;
        }

        PlaceAdminUpdateInput build() {
            return new PlaceAdminUpdateInput(
                    name,
                    addressProvided, addressRoad, addressJibun,
                    telProvided, tel,
                    false, null,
                    false, null,
                    false, null,
                    overviewProvided, overview,
                    false, null,
                    false, null,
                    status);
        }
    }

    /**
     * 리플렉션으로 값을 넣습니다.
     *
     * 검사를 위해 엔티티에 setter 를 여는 것보다 낫습니다.
     * 그 setter 는 운영 코드에서 아무도 쓰지 않으면서 아무나 값을 바꿀 수 있게 만듭니다.
     * 같은 폴더의 PlaceQueryServiceTest 와 같은 방식입니다.
     */
    private static void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(name + " 을 넣지 못했습니다", e);
            }
        }
        throw new IllegalStateException(name + " 필드를 찾지 못했습니다");
    }
}
