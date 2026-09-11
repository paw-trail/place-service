package com.pawtrail.place.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.place.domain.enums.CoordSource;
import com.pawtrail.place.domain.enums.MatchMethod;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.model.Place;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 병합 판정 규칙을 고정합니다.
 *
 * 거짓 병합이 거짓 분리보다 훨씬 위험하므로 거부 조건을 특히 촘촘히 둡니다.
 * 거짓 병합은 강남점 조건에 홍대점 원문을 붙이는 식으로 틀린 근거를 만듭니다.
 *
 * 적재본 전량을 여기 넣지 않습니다.
 * 병합 쌍 148 개와 최종 17,333 행이라는 정답지 대조는 적재 이슈에서
 * 실제로 밀어 넣어 행 수를 세는 편이 진짜 검증입니다.
 */
class PlaceMatcherTest {

    private static Place place(String name, String nameNormalized, CoordSource coordSource) {
        return place(name, nameNormalized, coordSource, "37.5000000", "127.0000000");
    }

    /**
     * 좌표를 지정해 만듭니다. 거리 판정을 검사할 때 씁니다.
     */
    private static Place place(String name, String nameNormalized, CoordSource coordSource,
                               String lat, String lon) {
        Place p = Place.create(name, PlaceType.PARK, new BigDecimal(lat), new BigDecimal(lon));
        p.applyNormalized(nameNormalized, List.of(), "서울|종로구계동길37");
        p.applyCoordinate(new BigDecimal(lat), new BigDecimal(lon), coordSource);
        return p;
    }

    // 위도 1도가 약 111km 이므로 0.0009 도가 대략 100m 임
    private static final String LAT_BASE = "37.5000000";
    private static final String LAT_150M = "37.5013500";   // 약 150m
    private static final String LAT_250M = "37.5022500";   // 약 250m
    private static final String LAT_400M = "37.5036000";   // 약 400m
    private static final String LON_BASE = "127.0000000";

    @Nested
    @DisplayName("주소 일치")
    class ByAddress {

        @Test
        @DisplayName("이름이 같으면 붙는다")
        void 이름이_같으면_붙는다() {
            Place incoming = place("고석정 꽃밭", "고석정꽃밭", CoordSource.ORIGINAL);
            Place candidate = place("고석정꽃밭", "고석정꽃밭", CoordSource.ORIGINAL);

            PlaceMatcher.Match m = PlaceMatcher.matchByAddress(incoming, List.of(candidate));

            assertThat(m.matched()).isTrue();
            assertThat(m.method()).isEqualTo(MatchMethod.ADDRESS);
            assertThat(m.place()).isSameAs(candidate);
        }

        @Test
        @DisplayName("이름이 다르면 안 붙는다")
        void 이름이_다르면_안_붙는다() {
            // 한 건물에 여러 장소가 있으면 주소가 같아짐
            // 적재본에 주소가 같고 이름이 다른 쌍이 백 여 건 있음
            // 북촌8경과 북촌문화센터, 스타필드코엑스몰과 다이소코엑스몰점 같은 것들
            Place incoming = place("북촌 8경", "북촌8경", CoordSource.ORIGINAL);
            Place candidate = place("북촌문화센터", "북촌문화센터", CoordSource.ORIGINAL);

            assertThat(PlaceMatcher.matchByAddress(incoming, List.of(candidate)).matched()).isFalse();
        }

        @Test
        @DisplayName("후보가 없으면 안 붙는다")
        void 후보가_없으면_안_붙는다() {
            Place incoming = place("여의도한강공원", "여의도한강공원", CoordSource.ORIGINAL);
            assertThat(PlaceMatcher.matchByAddress(incoming, List.of()).matched()).isFalse();
            assertThat(PlaceMatcher.matchByAddress(incoming, null).matched()).isFalse();
        }

        @Test
        @DisplayName("정규화 이름이 없으면 안 붙는다")
        void 정규화_이름이_없으면_안_붙는다() {
            // 이름 일치가 모든 단계의 전제이므로 비교할 값이 없으면 판정하지 않음
            Place incoming = place("어떤장소", null, CoordSource.ORIGINAL);
            Place candidate = place("어떤장소", "어떤장소", CoordSource.ORIGINAL);

            assertThat(PlaceMatcher.matchByAddress(incoming, List.of(candidate)).matched()).isFalse();
        }
    }

    @Nested
    @DisplayName("좌표 근접")
    class ByCoordinate {

        @Test
        @DisplayName("원본 좌표끼리면 COORD_ORIGINAL 이다")
        void 원본끼리() {
            Place incoming = place("문암생태공원", "문암생태공원", CoordSource.ORIGINAL);
            Place candidate = place("문암생태공원", "문암생태공원", CoordSource.ORIGINAL);

            PlaceMatcher.Match m = PlaceMatcher.matchByCoordinate(incoming, List.of(candidate));

            assertThat(m.matched()).isTrue();
            assertThat(m.method()).isEqualTo(MatchMethod.COORD_ORIGINAL);
            // 좌표가 같아 거리가 0m 이므로 높은 쪽임
            // 신뢰도는 검증할 때 낮은 것부터 뽑아 눈으로 확인하기 위한 값임
            assertThat(m.confidence()).isEqualTo(new BigDecimal("0.90"));
        }

        @Test
        @DisplayName("원본끼리도 멀면 신뢰도가 낮다")
        void 멀면_신뢰도가_낮다() {
            Place incoming = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, LAT_BASE, LON_BASE);
            Place far = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, "37.5007000", LON_BASE);

            PlaceMatcher.Match m = PlaceMatcher.matchByCoordinate(incoming, List.of(far));

            assertThat(m.matched()).isTrue();
            assertThat(m.confidence()).isEqualTo(new BigDecimal("0.80"));
        }

        @Test
        @DisplayName("한쪽만 지오코딩이면 COORD_GEOCODED 이고 신뢰도가 낮다")
        void 한쪽만_지오코딩() {
            Place incoming = place("어떤캠핑장", "어떤캠핑장", CoordSource.GEOCODED);
            Place candidate = place("어떤캠핑장", "어떤캠핑장", CoordSource.ORIGINAL);

            PlaceMatcher.Match m = PlaceMatcher.matchByCoordinate(incoming, List.of(candidate));

            assertThat(m.method()).isEqualTo(MatchMethod.COORD_GEOCODED);
            assertThat(m.confidence()).isEqualTo(new BigDecimal("0.60"));
        }

        @Test
        @DisplayName("지오코딩 좌표끼리는 안 붙는다")
        void 지오코딩끼리는_안_붙는다() {
            // 둘 다 주소에서 만든 값이라 오차가 겹치면
            // 서로 다른 장소가 같은 좌표로 보임
            Place incoming = place("어떤장소", "어떤장소", CoordSource.GEOCODED);
            Place candidate = place("어떤장소", "어떤장소", CoordSource.GEOCODED);

            assertThat(PlaceMatcher.matchByCoordinate(incoming, List.of(candidate)).matched()).isFalse();
        }

        @Test
        @DisplayName("이름이 다르면 안 붙는다")
        void 이름이_다르면_안_붙는다() {
            // 100m 안에는 서로 다른 장소가 얼마든지 있음
            Place incoming = place("멍멍카페", "멍멍카페", CoordSource.ORIGINAL);
            Place candidate = place("야옹카페", "야옹카페", CoordSource.ORIGINAL);

            assertThat(PlaceMatcher.matchByCoordinate(incoming, List.of(candidate)).matched()).isFalse();
        }

        @Test
        @DisplayName("지오코딩 후보를 건너뛰고 다음 후보를 본다")
        void 지오코딩을_건너뛴다() {
            Place incoming = place("어떤장소", "어떤장소", CoordSource.GEOCODED);
            Place skipped = place("어떤장소", "어떤장소", CoordSource.GEOCODED);
            Place taken = place("어떤장소", "어떤장소", CoordSource.CONVERTED);

            PlaceMatcher.Match m =
                    PlaceMatcher.matchByCoordinate(incoming, List.of(skipped, taken));

            assertThat(m.matched()).isTrue();
            assertThat(m.place()).isSameAs(taken);
        }
    }

    @Nested
    @DisplayName("거리 임계값")
    class Distance {

        @Test
        @DisplayName("원본끼리는 100m 를 넘으면 안 붙는다")
        void 원본은_100m() {
            Place incoming = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, LAT_BASE, LON_BASE);
            Place far = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, LAT_150M, LON_BASE);

            assertThat(PlaceMatcher.matchByCoordinate(incoming, List.of(far)).matched()).isFalse();
        }

        @Test
        @DisplayName("한쪽이 지오코딩이면 300m 까지 붙는다")
        void 지오코딩이_끼면_300m() {
            Place incoming = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, LAT_BASE, LON_BASE);
            Place geocoded = place("어떤장소", "어떤장소", CoordSource.GEOCODED, LAT_250M, LON_BASE);

            PlaceMatcher.Match m = PlaceMatcher.matchByCoordinate(incoming, List.of(geocoded));

            assertThat(m.matched()).isTrue();
            assertThat(m.method()).isEqualTo(MatchMethod.COORD_GEOCODED);
        }

        @Test
        @DisplayName("지오코딩이 껴도 300m 를 넘으면 안 붙는다")
        void 지오코딩도_300m_까지() {
            Place incoming = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, LAT_BASE, LON_BASE);
            Place tooFar = place("어떤장소", "어떤장소", CoordSource.GEOCODED, LAT_400M, LON_BASE);

            assertThat(PlaceMatcher.matchByCoordinate(incoming, List.of(tooFar)).matched()).isFalse();
        }

        @Test
        @DisplayName("어느 쪽이 먼저 들어와도 결과가 같다")
        void 순서에_안_갈린다() {
            // 반경을 incoming 의 좌표 출처로만 정하면 이 쌍이 한 방향에서만 붙었음
            // 같은 쌍이 적재 순서로 갈리면 안 됨
            Place original = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, LAT_BASE, LON_BASE);
            Place geocoded = place("어떤장소", "어떤장소", CoordSource.GEOCODED, LAT_250M, LON_BASE);

            assertThat(PlaceMatcher.matchByCoordinate(original, List.of(geocoded)).matched()).isTrue();
            assertThat(PlaceMatcher.matchByCoordinate(geocoded, List.of(original)).matched()).isTrue();
        }

        @Test
        @DisplayName("가장 가까운 후보를 고른다")
        void 가장_가까운_것() {
            Place incoming = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, LAT_BASE, LON_BASE);
            Place near = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, "37.5002000", LON_BASE);
            Place mid = place("어떤장소", "어떤장소", CoordSource.ORIGINAL, "37.5007000", LON_BASE);

            PlaceMatcher.Match m = PlaceMatcher.matchByCoordinate(incoming, List.of(mid, near));

            assertThat(m.place()).isSameAs(near);
        }

        @Test
        @DisplayName("조회 반경은 넓은 쪽으로 고정된다")
        void 조회_반경은_고정() {
            assertThat(PlaceMatcher.SEARCH_METERS).isEqualTo(PlaceMatcher.GEOCODED_METERS);
        }
    }
}
