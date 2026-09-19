package com.pawtrail.place.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 주소 정규화 규칙을 고정합니다.
 *
 * address_normalized 는 병합 일 순위 키입니다.
 * 이 값이 틀리면 병합이 통째로 틀리는데 오류가 나지 않아 알아채기 어렵습니다.
 * 그래서 규칙 하나하나를 검사로 못박아 둡니다.
 *
 * 값은 전부 적재본에 실재하는 주소입니다.
 */
class AddressNormalizerTest {

    @Nested
    @DisplayName("시도 표기 통일")
    class SidoCanonical {

        @Test
        @DisplayName("긴 표기를 먼저 맞춘다")
        void 긴_표기를_먼저_맞춘다() {
            // 강원을 먼저 맞추면 강원특별자치도에서 특별자치도가 남음
            assertThat(AddressNormalizer.normalize("강원특별자치도 고성군 현내면 대진리 16-10", null, null))
                    .isEqualTo("강원|고성군현내면대진리16-10");
        }

        @Test
        @DisplayName("같은 지역의 세 표기가 한 값으로 모인다")
        void 세_표기가_한_값으로() {
            // 고캠핑 한 소스 안에서만 강원이 세 갈래로 옴
            String a = AddressNormalizer.normalize("강원특별자치도 양양군 현북면", null, null);
            String b = AddressNormalizer.normalize("강원도 양양군 현북면", null, null);
            String c = AddressNormalizer.normalize("강원 양양군 현북면", null, null);
            assertThat(a).isEqualTo(b).isEqualTo(c);
        }
    }

    @Nested
    @DisplayName("통합 명칭 되돌리기")
    class MergedSido {

        @Test
        @DisplayName("광주 자치구면 광주로 간다")
        void 광주_자치구면_광주() {
            assertThat(AddressNormalizer.normalize("전남광주통합특별시 남구 사직길 49", null, null))
                    .isEqualTo("광주|남구사직길49");
        }

        @Test
        @DisplayName("그 밖은 전남으로 간다")
        void 그_밖은_전남() {
            assertThat(AddressNormalizer.normalize("전남광주통합특별시 담양군 담양읍", null, null))
                    .isEqualTo("전남|담양군담양읍");
        }

        @Test
        @DisplayName("분리 명칭으로 온 주소와 같은 값이 된다")
        void 분리_명칭과_붙는다() {
            // 이것이 통합 명칭을 되돌리는 이유임
            // 그대로 두면 공사 365 건이 문화정보원 922 건과 영영 붙지 않음
            String merged = AddressNormalizer.normalize("전남광주통합특별시 남구 사직길 49", null, null);
            String split = AddressNormalizer.normalize("광주광역시 남구 사직길 49", null, null);
            assertThat(merged).isEqualTo(split);
        }
    }

    @Nested
    @DisplayName("시도 폴백")
    class Fallback {

        @Test
        @DisplayName("주소에 시도가 없으면 소스가 준 시도를 쓴다")
        void 소스_시도를_쓴다() {
            // 고캠핑 웰빙문화테마마을은 addr1 에 시도가 빠져 있으나 doNm 이 멀쩡함
            assertThat(AddressNormalizer.normalize("영덕군 남정면 남호리 산26번지", null, "경상북도"))
                    .isEqualTo("경북|영덕군남정면남호리산26번지");
        }

        @Test
        @DisplayName("시도 표기가 짧게 와도 폴백이 받는다")
        void 짧은_표기도_받는다() {
            // 인천시는 시도 표기 목록에 있어 폴백까지 가지 않음
            assertThat(AddressNormalizer.normalize("인천시 강화군 내가면 강화서로 227번길 91", null, null))
                    .isEqualTo("인천|강화군내가면강화서로227번길91");
        }

        @Test
        @DisplayName("시도를 끝내 못 찾으면 null 이다")
        void 못_찾으면_null() {
            // 시도 없이 나머지만 담으면 다른 시도의 같은 도로명과 붙을 수 있음
            assertThat(AddressNormalizer.normalize("어딘가 이상한로 12", null, null)).isNull();
        }
    }

    @Nested
    @DisplayName("시도명과 도로명 경계")
    class Boundary {

        @Test
        @DisplayName("더 긴 시도 표기가 먼저 맞는다")
        void 통합_명칭이_먼저_맞는다() {
            // 전남광주통합특별시는 전남으로도 시작함
            // 전남이 먼저 맞으면 광주통합특별시가 주소 나머지로 남음
            // Sido 가 통합 명칭까지 한 목록에 담아 길이 내림차순으로 찾아 구조가 막음
            assertThat(AddressNormalizer.normalize("전남광주통합특별시 순천시 순천만길 513-25", null, null))
                    .isEqualTo("전남|순천시순천만길513-25");
        }

        @Test
        @DisplayName("시도명으로 시작하는 도로명을 시도로 보지 않는다")
        void 도로명을_시도로_보지_않는다() {
            // 실재하는 도로명들임
            // 경계를 두지 않으면 전남대학로가 전라남도로 판정되어 시도가 아예 다른 곳이 됨
            // 그 도로는 광주에 있음
            assertThat(AddressNormalizer.normalize("서울숲길 17", null, "경기도"))
                    .isEqualTo("경기|서울숲길17");
            assertThat(AddressNormalizer.normalize("전남대학로 1", null, "광주광역시"))
                    .isEqualTo("광주|전남대학로1");
            assertThat(AddressNormalizer.normalize("강원대학로 1", null, "강원특별자치도"))
                    .isEqualTo("강원|강원대학로1");
        }

        @Test
        @DisplayName("주소가 시도로만 이뤄져도 받는다")
        void 시도만_있어도_받는다() {
            // 경계 검사가 문자열 끝도 경계로 봐야 함
            assertThat(AddressNormalizer.normalize("서울 종로구", null, null))
                    .isEqualTo("서울|종로구");
        }

        @Test
        @DisplayName("시도와 다음 토큰이 붙어 와도 처리한다")
        void 붙어_와도_처리한다() {
            // 공백이 없는 형태로 오는 소스가 있을 수 있음
            assertThat(AddressNormalizer.normalize("서울특별시종로구계동길37", null, null))
                    .isEqualTo("서울|종로구계동길37");
        }
    }

    @Nested
    @DisplayName("행정 개편 매핑")
    class DistrictAlias {

        @Test
        @DisplayName("영종구를 중구로 맞춘다")
        void 영종구를_중구로() {
            // 좌표로는 못 붙음, 두 행이 151m 떨어져 임계값 100m 를 넘음
            String petTour = AddressNormalizer.normalize(
                    "인천광역시 영종구 마시란로 118 (덕교동)", null, null);
            String culture = AddressNormalizer.normalize(
                    "인천광역시 중구 마시란로 118", null, null);
            assertThat(petTour).isEqualTo(culture).isEqualTo("인천|중구마시란로118");
        }
    }

    @Nested
    @DisplayName("그 밖")
    class Etc {

        @Test
        @DisplayName("괄호를 버린다")
        void 괄호를_버린다() {
            // 도로명 뒤 법정동 표기라 소스마다 있고 없고가 갈림
            assertThat(AddressNormalizer.normalize("서울특별시 종로구 계동길 37 (계동)", null, null))
                    .isEqualTo("서울|종로구계동길37");
        }

        @Test
        @DisplayName("도로명이 없으면 지번을 쓴다")
        void 지번_폴백() {
            // 문화정보원은 지번을 100% 채우고 공사 계열은 0% 임
            assertThat(AddressNormalizer.normalize(null, "충청남도 공주시 반포면 상신리 594-5", null))
                    .isEqualTo("충남|공주시반포면상신리594-5");
        }

        @Test
        @DisplayName("주소가 아예 없으면 null 이다")
        void 주소가_없으면_null() {
            assertThat(AddressNormalizer.normalize(null, null, "경기도")).isNull();
        }

        @Test
        @DisplayName("시도만 따로 뽑을 수 있다")
        void 시도만_뽑기() {
            assertThat(AddressNormalizer.resolveSidoOnly("전남광주통합특별시 북구 용봉로 77", null, null))
                    .isEqualTo(Sido.GWANGJU);
            assertThat(Sido.GWANGJU.code()).isEqualTo("29");
        }
    }

    @Nested
    @DisplayName("법정동 코드")
    class LegalCode {

        @Test
        @DisplayName("길이와 무관하게 앞 두 자리가 시도다")
        void 앞_두_자리() {
            // 소스가 다섯 자리를 주는 행이 있음, 세종 고복자연공원의 36110 임
            assertThat(Sido.fromCode("36110")).isEqualTo(Sido.SEJONG);
            assertThat(Sido.fromCode("11")).isEqualTo(Sido.SEOUL);
        }

        @Test
        @DisplayName("통합 코드 12 는 답할 수 없다")
        void 통합_코드는_null() {
            // 전남인지 광주인지가 시군구에 달려 있음
            assertThat(Sido.fromCode("12")).isNull();
        }
    }

    @Nested
    @DisplayName("시군구 이름")
    class SigunguName {

        @Test
        @DisplayName("일반구가 있는 시는 구까지 붙인다")
        void 일반구는_시와_붙인다() {
            // 고양시에는 구가 셋이라 시만 담으면 세 구가 한 이름으로 뭉침
            assertThat(AddressNormalizer.resolveSigunguName("경기도 고양시 덕양구 동세로 19", null, null))
                    .isEqualTo("고양시 덕양구");
        }

        @Test
        @DisplayName("자치구와 군은 그 이름 하나다")
        void 자치구와_군() {
            assertThat(AddressNormalizer.resolveSigunguName("서울특별시 종로구 창경궁로 261 (명륜2가)", null, null))
                    .isEqualTo("종로구");
            assertThat(AddressNormalizer.resolveSigunguName("전라남도 신안군 자은면 자은서부2길 508-68", null, null))
                    .isEqualTo("신안군");
        }

        @Test
        @DisplayName("일반구가 없는 시는 시 이름만이다")
        void 일반구가_없는_시() {
            // 시 다음 토큰이 읍 · 면 · 도로명이면 붙이지 않음
            assertThat(AddressNormalizer.resolveSigunguName(null, "충청남도 공주시 반포면 상신리 594-5", null))
                    .isEqualTo("공주시");
        }

        @Test
        @DisplayName("세종은 시군구가 없어 null 이다")
        void 세종은_null() {
            // 문화정보원도 세종 행의 시군구 명칭을 비워 둠
            assertThat(AddressNormalizer.resolveSigunguName("세종특별자치시 한누리대로 2012", null, null))
                    .isNull();
            assertThat(AddressNormalizer.resolveSigunguName("세종특별자치시 조치원읍 충현로 159", null, null))
                    .isNull();
        }

        @Test
        @DisplayName("통합 명칭으로 와도 시군구를 뽑는다")
        void 통합_명칭() {
            assertThat(AddressNormalizer.resolveSigunguName("전남광주통합특별시 북구 용봉로 77", null, null))
                    .isEqualTo("북구");
        }

        @Test
        @DisplayName("개편 별칭을 쓰지 않고 적힌 대로 담는다")
        void 개편_별칭을_쓰지_않는다() {
            // 별칭 표는 병합 매칭 전용임, 이 값은 사람이 고르는 지역 이름임
            assertThat(AddressNormalizer.resolveSigunguName("인천광역시 영종구 마시란로 118 (덕교동)", null, null))
                    .isEqualTo("영종구");
        }

        @Test
        @DisplayName("주소에 시도가 없으면 소스가 준 시도를 거쳐 뽑는다")
        void 시도_폴백() {
            assertThat(AddressNormalizer.resolveSigunguName("영덕군 남정면 남호리 산26번지", null, "경상북도"))
                    .isEqualTo("영덕군");
        }

        @Test
        @DisplayName("시도를 끝내 못 찾거나 주소가 없으면 null 이다")
        void 못_찾으면_null() {
            // 시도 없이 시군구만 담으면 다른 시도의 같은 이름과 섞임
            assertThat(AddressNormalizer.resolveSigunguName("영덕군 남정면 남호리 산26번지", null, null)).isNull();
            assertThat(AddressNormalizer.resolveSigunguName(null, null, "경기도")).isNull();
        }
    }
}
