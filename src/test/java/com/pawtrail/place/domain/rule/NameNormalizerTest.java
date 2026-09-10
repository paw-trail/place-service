package com.pawtrail.place.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 이름 정규화 규칙을 고정합니다.
 *
 * 스프링도 데이터베이스도 띄우지 않습니다. 순수 함수라 그럴 이유가 없습니다.
 * 값은 전부 적재본에 실재하는 것을 씁니다. 지어낸 예시로는 규칙이 데이터에 맞는지 알 수 없습니다.
 */
class NameNormalizerTest {

    @Nested
    @DisplayName("정규화")
    class Normalize {

        @Test
        @DisplayName("공백을 없앤다")
        void 공백을_없앤다() {
            assertThat(NameNormalizer.normalize("이천 예스파크")).isEqualTo("이천예스파크");
        }

        @Test
        @DisplayName("지점명은 지우지 않는다")
        void 지점명은_지우지_않는다() {
            // 강남점과 홍대점은 서로 다른 장소임
            // 지우면 거짓 병합이 되고 그것은 틀린 근거를 만드는 일임
            assertThat(NameNormalizer.normalize("야옹아멍멍해봐 강릉내곡점"))
                    .isEqualTo("야옹아멍멍해봐강릉내곡점");
        }

        @Test
        @DisplayName("괄호는 지우지 않는다")
        void 괄호는_지우지_않는다() {
            // 괄호를 뗀 형태는 별칭으로 따로 담음
            assertThat(NameNormalizer.normalize("봉화산(서울)")).isEqualTo("봉화산(서울)");
        }

        @Test
        @DisplayName("빈 값과 공백만 있는 값은 null 이다")
        void 빈_값은_null() {
            assertThat(NameNormalizer.normalize(null)).isNull();
            assertThat(NameNormalizer.normalize("   ")).isNull();
        }
    }

    @Nested
    @DisplayName("별칭 추출")
    class ExtractAliases {

        @Test
        @DisplayName("괄호 안과 괄호를 뺀 본명을 둘 다 담는다")
        void 둘_다_담는다() {
            // 실제로 새로 붙은 다섯 쌍 중 넷이 본명 쪽에서 걸렸음
            assertThat(NameNormalizer.extractAliases("포천아트밸리 (한탄강 유네스코 세계지질공원)"))
                    .containsExactly("한탄강유네스코세계지질공원", "포천아트밸리");
        }

        @Test
        @DisplayName("괄호가 없으면 비어 있다")
        void 괄호가_없으면_비어_있다() {
            // 본명이 곧 정규화 이름이라 담을 이유가 없음
            assertThat(NameNormalizer.extractAliases("여의도한강공원")).isEmpty();
        }

        @Test
        @DisplayName("걸러내지 않는다")
        void 걸러내지_않는다() {
            // 봉화산(서울)의 서울은 동명이인을 가르는 값이라 별칭으로 쓰면 정반대로 작동함
            // 그래도 지금 거르지 않는 이유는 이 값을 매칭 축으로 쓰지 않기로 했기 때문임
            // 켤 때 걸러내며 그 규칙은 시도 토큰, 시군구, 두 자 이하, 본명 포함임
            assertThat(NameNormalizer.extractAliases("봉화산(서울)"))
                    .containsExactly("서울", "봉화산");
        }

        @Test
        @DisplayName("전각 괄호도 받는다")
        void 전각_괄호도_받는다() {
            assertThat(NameNormalizer.extractAliases("사천해변（사천해수욕장）"))
                    .contains("사천해수욕장");
        }

        @Test
        @DisplayName("정규화 이름과 같은 값은 담지 않는다")
        void 자기_자신은_담지_않는다() {
            // 괄호를 떼도 같은 문자열이 되는 경우
            assertThat(NameNormalizer.extractAliases("()")).isEmpty();
        }
    }
}
