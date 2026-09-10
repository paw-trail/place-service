package com.pawtrail.place.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 분류 매핑을 고정합니다.
 *
 * 코드가 무엇을 뜻하는지는 적재본의 실제 장소 이름을 전수로 확인해 정했습니다.
 * 검사 이름에 그 근거가 된 장소를 함께 적어 둡니다.
 */
class PlaceTypeMapperTest {

    @Nested
    @DisplayName("공사 계열")
    class PetTour {

        @Test
        @DisplayName("중분류를 기준으로 옮긴다")
        void 중분류_기준() {
            // VE 하나가 298 건인데 그 안이 공원과 마을, 전망대, 미술관으로 갈림
            // 대분류로 옮기면 미술관 스물한 곳이 공원 칩에 들어감
            assertThat(resolve("VE", "VE03", "VE030100")).isEqualTo(PlaceType.PARK);      // 여의도한강공원
            assertThat(resolve("VE", "VE04", "VE040200")).isEqualTo(PlaceType.CULTURE);   // 감천문화마을
            assertThat(resolve("VE", "VE07", "VE070600")).isEqualTo(PlaceType.CULTURE);   // 하슬라아트월드
            assertThat(resolve("VE", "VE01", "VE010200")).isEqualTo(PlaceType.LEISURE);   // 병방치 스카이워크
            assertThat(resolve("VE", "VE05", "VE050200")).isEqualTo(PlaceType.STAY);      // 동강시스타 리조트
        }

        @Test
        @DisplayName("걷기길만 소분류를 본다")
        void 걷기길만_소분류() {
            // LS01 은 레저 스포츠인데 LS011900 서른두 건이 전부 둘레길과 황톳길임
            assertThat(resolve("LS", "LS01", "LS011900")).isEqualTo(PlaceType.PARK);      // 계족산 황톳길
            assertThat(resolve("LS", "LS01", "LS010900")).isEqualTo(PlaceType.LEISURE);   // 원마운트 스노우파크
        }

        @Test
        @DisplayName("숙박과 야영장을 가른다")
        void 숙박과_야영장() {
            assertThat(resolve("AC", "AC03", null)).isEqualTo(PlaceType.STAY);            // 관광 꿈꾸는숲 펜션
            assertThat(resolve("AC", "AC05", null)).isEqualTo(PlaceType.CAMPING);         // 죽산보 오토캠핑장
        }

        @Test
        @DisplayName("카페와 식당을 가른다")
        void 카페와_식당() {
            assertThat(resolve("FD", "FD05", null)).isEqualTo(PlaceType.CAFE);            // 도깨비젤라또
            assertThat(resolve("FD", "FD01", null)).isEqualTo(PlaceType.RESTAURANT);      // 다경횟집
        }

        @Test
        @DisplayName("모르는 중분류는 대분류로 떨어진다")
        void 폴백() {
            // 소스가 분류를 늘려도 조용히 틀리지 않고 대략 맞는 자리로 감
            assertThat(resolve("NA", "NA99", null)).isEqualTo(PlaceType.PARK);
        }

        @Test
        @DisplayName("대분류도 모르면 ETC 다")
        void 최종_폴백() {
            // 판정을 포기하지 않고 담음
            assertThat(resolve("ZZ", "ZZ01", null)).isEqualTo(PlaceType.ETC);
        }

        private PlaceType resolve(String major, String middle, String minor) {
            return PlaceTypeMapper.resolve(SourceType.PET_TOUR, major, middle, minor);
        }
    }

    @Nested
    @DisplayName("고캠핑")
    class GoCamping {

        @Test
        @DisplayName("분류를 보지 않고 소스만 본다")
        void 소스만_본다() {
            // 고캠핑 API 가 야영장 정보 서비스라 정의상 전부 야영장임
            // induty 가 빈 행이 여섯 개 있어 그것으로 판정하면 구멍이 남음
            // 그 여섯은 이름에 글램핑과 캠핑장이 들어 있는 멀쩡한 야영장임
            assertThat(PlaceTypeMapper.resolve(SourceType.GOCAMPING, null, null, null))
                    .isEqualTo(PlaceType.CAMPING);
            assertThat(PlaceTypeMapper.resolve(SourceType.GOCAMPING, "일반야영장", null, null))
                    .isEqualTo(PlaceType.CAMPING);
        }

        @Test
        @DisplayName("부대시설이 섞여 온 값도 야영장이다")
        void 오염된_값도_야영장() {
            // induty 칸에 부대시설 목록이 통째로 들어간 행이 스물세 개 있음
            String polluted = "일반야영장,침대,TV,에어컨,냉장고,유무선인터넷,난방기구,취사도구,내부화장실";
            assertThat(PlaceTypeMapper.resolve(SourceType.GOCAMPING, polluted, null, null))
                    .isEqualTo(PlaceType.CAMPING);
        }
    }

    @Nested
    @DisplayName("문화정보원")
    class CultureCsv {

        @Test
        @DisplayName("카테고리3 을 기준으로 옮긴다")
        void 카테고리3_기준() {
            assertThat(resolve("동물병원")).isEqualTo(PlaceType.VET);
            assertThat(resolve("반려동물용품")).isEqualTo(PlaceType.ETC);
            assertThat(resolve("여행지")).isEqualTo(PlaceType.PARK);
            assertThat(resolve("박물관")).isEqualTo(PlaceType.CULTURE);
            assertThat(resolve("카페")).isEqualTo(PlaceType.CAFE);
            assertThat(resolve("펜션")).isEqualTo(PlaceType.STAY);
            assertThat(resolve("식당")).isEqualTo(PlaceType.RESTAURANT);
        }

        @Test
        @DisplayName("모르는 카테고리는 ETC 다")
        void 폴백() {
            assertThat(resolve("반려문화시설")).isEqualTo(PlaceType.ETC);
        }

        private PlaceType resolve(String category) {
            return PlaceTypeMapper.resolve(SourceType.CULTURE_CSV, null, category, null);
        }
    }

    @Nested
    @DisplayName("보급 지점")
    class SupplyPoint {

        @Test
        @DisplayName("반려동물 용품점을 보급 지점으로 본다")
        void 용품점은_보급_지점() {
            // 명세가 정해 둔 것임
            // 지도 범례가 동물병원을 placeType 이 VET 인 장소로
            // 반려견 간식과 용품점을 supplyPoint 가 true 인 장소로 가름
            assertThat(PlaceTypeMapper.isSupplyPoint(SourceType.CULTURE_CSV, "반려동물용품")).isTrue();
        }

        @Test
        @DisplayName("공사 계열은 용품점과 편의점만 본다")
        void 공사는_둘만() {
            assertThat(PlaceTypeMapper.isSupplyPoint(SourceType.PET_TOUR, "SH05")).isTrue();
            assertThat(PlaceTypeMapper.isSupplyPoint(SourceType.PET_TOUR, "SH07")).isTrue();
        }

        @Test
        @DisplayName("아울렛과 시장은 보급 지점이 아니다")
        void 몰과_시장은_아님() {
            // 그 자체가 목적지이지 동선 중에 들르는 곳이 아님
            assertThat(PlaceTypeMapper.isSupplyPoint(SourceType.PET_TOUR, "SH02")).isFalse();
            assertThat(PlaceTypeMapper.isSupplyPoint(SourceType.PET_TOUR, "SH06")).isFalse();
        }

        @Test
        @DisplayName("동물병원은 보급 지점이 아니다")
        void 병원은_아님() {
            // 범례에서 placeType 이 VET 인 장소로 따로 갈림
            assertThat(PlaceTypeMapper.isSupplyPoint(SourceType.CULTURE_CSV, "동물병원")).isFalse();
        }

        @Test
        @DisplayName("고캠핑은 보급 지점이 없다")
        void 고캠핑은_없음() {
            assertThat(PlaceTypeMapper.isSupplyPoint(SourceType.GOCAMPING, "일반야영장")).isFalse();
        }
    }
}
