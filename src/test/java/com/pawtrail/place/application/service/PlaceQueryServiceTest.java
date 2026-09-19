package com.pawtrail.place.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.place.application.dto.output.PlaceDetailOutput;
import com.pawtrail.place.application.dto.output.PlaceDocumentOutput;
import com.pawtrail.place.application.dto.output.PlaceIndexingOutput;
import com.pawtrail.place.application.dto.output.PlaceSummaryOutput;
import com.pawtrail.place.domain.enums.FacilityCode;
import com.pawtrail.place.domain.enums.MatchMethod;
import com.pawtrail.place.domain.enums.PlaceStatus;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.exception.PlaceErrorCode;
import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.model.PlaceFacility;
import com.pawtrail.place.domain.model.PlaceSourceLink;
import com.pawtrail.place.domain.provider.PlaceDocumentProvider;
import com.pawtrail.place.domain.provider.dto.RawDocumentView;
import com.pawtrail.place.domain.repository.PlaceFacilityRepository;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.domain.repository.PlaceSourceLinkRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 장소를 읽어 돌려주는 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 띄우지 않습니다.
 * 여기서 지키려는 것은 저장소가 준 행을 어떻게 응답으로 맞추는가이고
 * 그 판단에 데이터베이스가 관여하지 않습니다.
 * 조회 메서드가 저장소와 어긋나면 PlaceApplicationTests 가 기동에서 잡습니다.
 *
 * 특히 지키려는 것이 둘입니다.
 * 여러 장소 조회는 user 가 이미 그 동작에 기대고 있어,
 * 없는 식별자를 오류로 바꾸면 user 의 목록이 통째로 실패합니다.
 * 상세의 sources[] 는 같은 소스 안의 중복도 병합했기 때문에
 * 같은 소스가 두 번 붙은 장소가 실제로 있습니다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceQueryServiceTest {

    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");
    private static final UUID MISSING = UUID.fromString("cccccccc-0000-7000-8000-000000000003");

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceSourceLinkRepository placeSourceLinkRepository;

    @Mock
    private PlaceFacilityRepository placeFacilityRepository;

    @Mock
    private PlaceDocumentProvider placeDocumentProvider;

    @InjectMocks
    private PlaceQueryService placeQueryService;

    // ── 여러 장소 ─────────────────────────────────────────────

    @Test
    @DisplayName("요청한 순서대로 담고 없는 식별자는 뺀다")
    void 없는_식별자는_뺀다() {
        // 저장소는 순서를 보장하지 않으므로 일부러 거꾸로 돌려줌
        when(placeRepository.findAllById(anyCollection()))
                .thenReturn(List.of(place(PLACE_B, "B 공원"), place(PLACE_A, "A 카페")));

        List<PlaceSummaryOutput> result =
                placeQueryService.getSummaries(List.of(PLACE_A, MISSING, PLACE_B));

        assertThat(result).extracting(PlaceSummaryOutput::placeId).containsExactly(PLACE_A, PLACE_B);
    }

    @Test
    @DisplayName("전부 없으면 오류가 아니라 빈 목록이다")
    void 전부_없으면_빈_목록() {
        when(placeRepository.findAllById(anyCollection())).thenReturn(List.of());

        assertThat(placeQueryService.getSummaries(List.of(MISSING))).isEmpty();
    }

    @Test
    @DisplayName("폐업한 장소도 행이 있으므로 담는다")
    void 폐업해도_담는다() {
        Place closed = place(PLACE_A, "문 닫은 카페");
        closed.changeStatus(PlaceStatus.CLOSED);
        when(placeRepository.findAllById(anyCollection())).thenReturn(List.of(closed));

        assertThat(placeQueryService.getSummaries(List.of(PLACE_A)))
                .extracting(PlaceSummaryOutput::placeId)
                .containsExactly(PLACE_A);
    }

    @Test
    @DisplayName("중복과 null 은 걸러 한 번씩만 묻는다")
    void 중복과_null_은_거른다() {
        when(placeRepository.findAllById(anyCollection())).thenReturn(List.of(place(PLACE_A, "A 카페")));

        List<PlaceSummaryOutput> result =
                placeQueryService.getSummaries(Arrays.asList(PLACE_A, null, PLACE_A));

        verify(placeRepository).findAllById(List.of(PLACE_A));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("물어볼 것이 없으면 저장소를 부르지 않는다")
    void 빈_요청() {
        assertThat(placeQueryService.getSummaries(List.of())).isEmpty();

        verifyNoInteractions(placeRepository);
    }

    @Test
    @DisplayName("user 가 받는 일곱 필드를 채운다")
    void 일곱_필드() {
        Place place = place(PLACE_A, "A 용품점");
        place.applyClassification(PlaceType.ETC, null, "반려동물용품", null);
        place.applyContact(null, null, null, null, "https://example.com/a.jpg", null);
        place.changeSupplyPoint(true);
        when(placeRepository.findAllById(anyCollection())).thenReturn(List.of(place));

        PlaceSummaryOutput summary = placeQueryService.getSummaries(List.of(PLACE_A)).get(0);

        assertThat(summary.placeId()).isEqualTo(PLACE_A);
        assertThat(summary.name()).isEqualTo("A 용품점");
        assertThat(summary.placeType()).isEqualTo(PlaceType.ETC);
        assertThat(summary.imageUrl()).isEqualTo("https://example.com/a.jpg");
        assertThat(summary.lat()).isEqualByComparingTo(new BigDecimal("37.5"));
        assertThat(summary.lon()).isEqualByComparingTo(new BigDecimal("127.0"));
        assertThat(summary.supplyPoint()).isTrue();
    }

    // ── 상세 ─────────────────────────────────────────────────

    @Test
    @DisplayName("없는 장소는 PLACE_NOT_FOUND 다")
    void 없는_장소() {
        when(placeRepository.findById(MISSING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeQueryService.getDetail(MISSING))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 소스는 한 번만 담고 대표 순서로 놓는다")
    void 소스는_한_번씩_대표_순서로() {
        // 문화정보원 레코드 둘이 병합돼 같은 소스가 두 번 붙은 장소임
        // 띄어쓰기만 다른 시설명이 다른 키가 되어 실제 적재본에 이런 행이 있음
        givenDetail(place(PLACE_A, "A 공원"),
                List.of(
                        PlaceSourceLink.link(PLACE_A, SourceType.CULTURE_CSV, "A 공원|서울 1",
                                MatchMethod.ADDRESS, null),
                        PlaceSourceLink.createPrimary(PLACE_A, SourceType.PET_TOUR, "126508"),
                        PlaceSourceLink.link(PLACE_A, SourceType.CULTURE_CSV, "A공원|서울 1",
                                MatchMethod.ADDRESS, null)),
                List.of());

        PlaceDetailOutput detail = placeQueryService.getDetail(PLACE_A);

        assertThat(detail.sources()).containsExactly(
                new PlaceDetailOutput.Source(SourceType.PET_TOUR, "한국관광공사"),
                new PlaceDetailOutput.Source(SourceType.CULTURE_CSV, "문화정보원"));
    }

    @Test
    @DisplayName("도로명이 있으면 도로명을 주소로 쓴다")
    void 도로명이_먼저() {
        Place place = place(PLACE_A, "여의도한강공원");
        place.applyAddress("서울특별시 영등포구 여의동로 330", "서울특별시 영등포구 여의도동 8", "11", "영등포구");
        givenDetail(place, List.of(), List.of());

        assertThat(placeQueryService.getDetail(PLACE_A).address()).isEqualTo("서울특별시 영등포구 여의동로 330");
    }

    @Test
    @DisplayName("도로명이 없으면 지번을 주소로 쓴다")
    void 도로명이_없으면_지번() {
        Place place = place(PLACE_A, "여의도한강공원");
        place.applyAddress(null, "서울특별시 영등포구 여의도동 8", "11", "영등포구");
        givenDetail(place, List.of(), List.of());

        assertThat(placeQueryService.getDetail(PLACE_A).address()).isEqualTo("서울특별시 영등포구 여의도동 8");
    }

    @Test
    @DisplayName("편의시설은 선언 순서로 담는다")
    void 편의시설_순서() {
        givenDetail(place(PLACE_A, "A 캠핑장"), List.of(),
                List.of(PlaceFacility.create(PLACE_A, FacilityCode.RESERVATION),
                        PlaceFacility.create(PLACE_A, FacilityCode.PARKING)));

        assertThat(placeQueryService.getDetail(PLACE_A).facilities())
                .containsExactly(FacilityCode.PARKING, FacilityCode.RESERVATION);
    }

    @Test
    @DisplayName("편의시설이 없으면 null 이 아니라 빈 배열이다")
    void 편의시설_없음() {
        givenDetail(place(PLACE_A, "A 공원"), List.of(), List.of());

        assertThat(placeQueryService.getDetail(PLACE_A).facilities()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("폐업한 장소도 상세가 나가고 status 로 알린다")
    void 폐업한_장소의_상세() {
        Place closed = place(PLACE_A, "문 닫은 카페");
        closed.changeStatus(PlaceStatus.CLOSED);
        givenDetail(closed, List.of(), List.of());

        assertThat(placeQueryService.getDetail(PLACE_A).status()).isEqualTo(PlaceStatus.CLOSED);
    }

    // ── 색인용 ───────────────────────────────────────────────

    @Nested
    @DisplayName("색인용 조회")
    class 색인용_조회 {

        @Test
        @DisplayName("식별자로 읽으면 요청 순서대로 담고 없는 것 · 중복 · null 은 뺀다")
        void 식별자로_읽기() {
            // 저장소는 순서를 보장하지 않으므로 일부러 거꾸로 돌려줌
            when(placeRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(place(PLACE_B, "B 공원"), place(PLACE_A, "A 카페")));
            when(placeFacilityRepository.findAllByPlaceIdIn(anyCollection())).thenReturn(List.of());

            List<PlaceIndexingOutput> result = placeQueryService.getIndexingByIds(
                    Arrays.asList(PLACE_A, MISSING, null, PLACE_A, PLACE_B));

            verify(placeRepository).findAllById(List.of(PLACE_A, MISSING, PLACE_B));
            assertThat(result).extracting(PlaceIndexingOutput::placeId).containsExactly(PLACE_A, PLACE_B);
        }

        @Test
        @DisplayName("물어볼 것이 없으면 저장소를 부르지 않는다")
        void 빈_요청() {
            assertThat(placeQueryService.getIndexingByIds(List.of())).isEmpty();

            verifyNoInteractions(placeRepository, placeFacilityRepository);
        }

        @Test
        @DisplayName("열여섯 칸을 채우고 편의시설은 한 번에 읽어 선언 순서로 담는다")
        void 열여섯_칸() {
            Place place = place(PLACE_A, "여의도한강공원");
            place.applyNormalized("여의도한강공원", List.of("여의도공원"), "서울|영등포구여의동로330");
            place.applyAddress("서울특별시 영등포구 여의동로 330", "서울특별시 영등포구 여의도동 8", "11", "영등포구");
            place.applyContact(null, null, null, null, "https://example.com/a.jpg", null);
            place.applyDescription("한강을 따라 걷는 공원입니다", null, null);
            place.applyDataBaseDate(LocalDate.of(2025, 3, 24));
            place.changeStatus(PlaceStatus.ACTIVE);
            setField(place, "updatedAt", LocalDateTime.of(2026, 9, 19, 12, 0));
            when(placeRepository.findAllById(anyCollection())).thenReturn(List.of(place));
            when(placeFacilityRepository.findAllByPlaceIdIn(List.of(PLACE_A))).thenReturn(List.of(
                    PlaceFacility.create(PLACE_A, FacilityCode.RESERVATION),
                    PlaceFacility.create(PLACE_A, FacilityCode.PARKING)));

            PlaceIndexingOutput out = placeQueryService.getIndexingByIds(List.of(PLACE_A)).get(0);

            assertThat(out.placeId()).isEqualTo(PLACE_A);
            assertThat(out.name()).isEqualTo("여의도한강공원");
            assertThat(out.nameAlias()).containsExactly("여의도공원");
            assertThat(out.placeType()).isEqualTo(PlaceType.PARK);
            assertThat(out.addressRoad()).isEqualTo("서울특별시 영등포구 여의동로 330");
            assertThat(out.addressJibun()).isEqualTo("서울특별시 영등포구 여의도동 8");
            assertThat(out.sidoCode()).isEqualTo("11");
            assertThat(out.sigunguName()).isEqualTo("영등포구");
            assertThat(out.lat()).isEqualByComparingTo(new BigDecimal("37.5"));
            assertThat(out.lon()).isEqualByComparingTo(new BigDecimal("127.0"));
            assertThat(out.facilities()).containsExactly(FacilityCode.PARKING, FacilityCode.RESERVATION);
            assertThat(out.status()).isEqualTo(PlaceStatus.ACTIVE);
            assertThat(out.imageUrl()).isEqualTo("https://example.com/a.jpg");
            assertThat(out.overview()).isEqualTo("한강을 따라 걷는 공원입니다");
            assertThat(out.dataBaseDate()).isEqualTo(LocalDate.of(2025, 3, 24));
            assertThat(out.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 19, 12, 0));
        }

        @Test
        @DisplayName("별칭과 편의시설이 없으면 null 이 아니라 빈 목록이다")
        void 빈_목록() {
            when(placeRepository.findAllById(anyCollection())).thenReturn(List.of(place(PLACE_A, "A 공원")));
            when(placeFacilityRepository.findAllByPlaceIdIn(anyCollection())).thenReturn(List.of());

            PlaceIndexingOutput out = placeQueryService.getIndexingByIds(List.of(PLACE_A)).get(0);

            assertThat(out.nameAlias()).isNotNull().isEmpty();
            assertThat(out.facilities()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("이어 읽을 때는 after 를 그대로 넘기고 개수를 1 에서 500 사이로 맞춘다")
        void 이어_읽기() {
            when(placeRepository.findPageAfter(any(), anyInt())).thenReturn(List.of());

            assertThat(placeQueryService.getIndexingAfter(PLACE_A, 9999)).isEmpty();
            assertThat(placeQueryService.getIndexingAfter(null, 0)).isEmpty();

            verify(placeRepository).findPageAfter(PLACE_A, 500);
            verify(placeRepository).findPageAfter(null, 1);
            // 빈 쪽이면 편의시설을 묻지 않음
            verifyNoInteractions(placeFacilityRepository);
        }

        @Test
        @DisplayName("이어 읽은 쪽의 편의시설도 한 번에 읽어 장소별로 나눈다")
        void 이어_읽은_쪽의_편의시설() {
            when(placeRepository.findPageAfter(null, 500))
                    .thenReturn(List.of(place(PLACE_A, "A 공원"), place(PLACE_B, "B 캠핑장")));
            when(placeFacilityRepository.findAllByPlaceIdIn(List.of(PLACE_A, PLACE_B)))
                    .thenReturn(List.of(PlaceFacility.create(PLACE_B, FacilityCode.PLAYGROUND)));

            List<PlaceIndexingOutput> page = placeQueryService.getIndexingAfter(null, 500);

            assertThat(page).extracting(PlaceIndexingOutput::placeId).containsExactly(PLACE_A, PLACE_B);
            assertThat(page.get(0).facilities()).isEmpty();
            assertThat(page.get(1).facilities()).containsExactly(FacilityCode.PLAYGROUND);
        }
    }

    // 상세 조회가 부르는 저장소 셋을 한 번에 채움
    private void givenDetail(Place place, List<PlaceSourceLink> links, List<PlaceFacility> facilities) {
        UUID placeId = place.getId();
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(placeSourceLinkRepository.findAllByPlaceId(placeId)).thenReturn(links);
        when(placeFacilityRepository.findAllByPlaceId(placeId)).thenReturn(facilities);
    }

    /**
     * 식별자를 채운 장소를 만듭니다.
     *
     * 식별자는 저장할 때 하이버네이트가 만들어 넣으므로 생성 직후에는 비어 있습니다.
     */

    // ── 원문 보기 ─────────────────────────────────────────────

    @Nested
    @DisplayName("원문 보기")
    class 원문_보기 {

        @Test
        @DisplayName("표시 이름을 붙여 돌려준다")
        void 표시_이름() {
            when(placeRepository.existsById(PLACE_A)).thenReturn(true);
            when(placeDocumentProvider.findByPlaceId(PLACE_A))
                    .thenReturn(List.of(view("PET_TOUR"), view("CULTURE_CSV")));

            // 상세의 sources[] 가 같은 값을 담으므로 같은 화면에서 모양이 갈리면 안 됨
            List<PlaceDocumentOutput> documents =
                    placeQueryService.getDocuments(PLACE_A).documents();

            assertThat(documents).hasSize(2);
            assertThat(documents.get(0).source()).isEqualTo(SourceType.PET_TOUR);
            assertThat(documents.get(0).sourceLabel())
                    .isEqualTo(SourceType.PET_TOUR.label());
            assertThat(documents.get(1).sourceLabel())
                    .isEqualTo(SourceType.CULTURE_CSV.label());
        }

        @Test
        @DisplayName("받은 순서를 그대로 지킨다")
        void 순서_유지() {
            when(placeRepository.existsById(PLACE_A)).thenReturn(true);
            when(placeDocumentProvider.findByPlaceId(PLACE_A)).thenReturn(
                    List.of(view("PET_TOUR"), view("GOCAMPING"), view("CULTURE_CSV")));

            // 정렬은 받아 온 쪽이 이미 대표 순서로 맞춰 줌
            // 여기서 다시 정렬하면 그쪽 규칙이 바뀔 때 두 곳을 고쳐야 함
            assertThat(placeQueryService.getDocuments(PLACE_A).documents())
                    .extracting(PlaceDocumentOutput::source)
                    .containsExactly(
                            SourceType.PET_TOUR, SourceType.GOCAMPING, SourceType.CULTURE_CSV);
        }

        @Test
        @DisplayName("모르는 소스는 그 항목만 뺀다")
        void 모르는_소스() {
            when(placeRepository.existsById(PLACE_A)).thenReturn(true);
            when(placeDocumentProvider.findByPlaceId(PLACE_A))
                    .thenReturn(List.of(view("PET_TOUR"), view("SOMETHING_NEW")));

            // 한쪽에만 소스가 늘 수 있음
            // 화면을 통째로 죽이면 볼 수 있는 원문까지 못 보게 됨
            List<PlaceDocumentOutput> documents =
                    placeQueryService.getDocuments(PLACE_A).documents();

            assertThat(documents).hasSize(1);
            assertThat(documents.get(0).source()).isEqualTo(SourceType.PET_TOUR);
        }

        @Test
        @DisplayName("원문이 없으면 빈 목록이다")
        void 빈_목록() {
            when(placeRepository.existsById(PLACE_A)).thenReturn(true);
            when(placeDocumentProvider.findByPlaceId(PLACE_A)).thenReturn(List.of());

            // 원본을 거치지 않는 소스로만 만들어진 장소가 여기로 옴
            assertThat(placeQueryService.getDocuments(PLACE_A).documents()).isEmpty();
        }

        @Test
        @DisplayName("없는 장소면 물어보지 않고 PLACE_NOT_FOUND 다")
        void 없는_장소() {
            when(placeRepository.existsById(MISSING)).thenReturn(false);

            assertThatThrownBy(() -> placeQueryService.getDocuments(MISSING))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);

            // 없는 장소에 물으면 상대가 빈 목록을 주는데
            // 그러면 "장소가 없어서 없는 것" 이 "못 가져온 것" 과 섞임
            verify(placeDocumentProvider, never()).findByPlaceId(any());
        }

        @Test
        @DisplayName("가져오지 못하면 빈 목록으로 바꾸지 않는다")
        void 실패는_그대로() {
            when(placeRepository.existsById(PLACE_A)).thenReturn(true);
            when(placeDocumentProvider.findByPlaceId(PLACE_A))
                    .thenThrow(new CustomException(PlaceErrorCode.PLACE_DOCUMENTS_UNAVAILABLE));

            // 원문이 정말 없는 것과 지금 못 가져오는 것은 다른 상태임
            assertThatThrownBy(() -> placeQueryService.getDocuments(PLACE_A))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(PlaceErrorCode.PLACE_DOCUMENTS_UNAVAILABLE);
        }

        private RawDocumentView view(String source) {
            return new RawDocumentView(
                    source, "문암생태공원", "[동반 유형] 일부구역 동반가능",
                    LocalDateTime.of(2025, 9, 16, 15, 31, 47),
                    LocalDateTime.of(2026, 9, 9, 14, 22, 57));
        }
    }

    private static Place place(UUID id, String name) {
        Place place = Place.create(name, PlaceType.PARK,
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"));
        setField(place, "id", id);
        return place;
    }

    /**
     * 리플렉션으로 값을 넣습니다.
     *
     * 검사를 위해 엔티티에 setter 를 여는 것보다 낫습니다.
     * 그 setter 는 운영 코드에서 아무도 쓰지 않으면서 아무나 값을 바꿀 수 있게 만듭니다.
     * user 의 FavoriteServiceTest 와 같은 방식입니다.
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
