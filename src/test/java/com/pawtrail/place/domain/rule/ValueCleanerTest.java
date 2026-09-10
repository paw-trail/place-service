package com.pawtrail.place.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 값 다듬기 규칙을 고정합니다.
 *
 * 값은 전부 적재본에 실재하는 것입니다.
 */
class ValueCleanerTest {

    @Nested
    @DisplayName("전화번호")
    class Phone {

        @Test
        @DisplayName("안내 문구에서 첫 번호를 뽑는다")
        void 첫_번호를_뽑는다() {
            // 공사의 infocenter 는 번호가 아니라 안내 문구임
            // 실물에서 앞이 그 장소 직통이고 뒤가 관공서임
            String raw = "북촌마을안내소 02-2148-4161\n서울 종로구청 관광체육과 02-2148-1858";
            assertThat(ValueCleaner.extractPhone(raw)).isEqualTo("02-2148-4161");
        }

        @Test
        @DisplayName("br 태그가 붙어 있어도 뽑는다")
        void br_태그가_붙어도() {
            // 033-572-301<br> 처럼 태그가 번호에 붙어 오는 행이 열두 개 있음
            // 태그를 먼저 걷어내지 않으면 자릿수 판정이 어긋남
            String raw = "근덕면해수욕장 033-572-3011<br>\n근덕면사무소 033-570-4806";
            assertThat(ValueCleaner.extractPhone(raw)).isEqualTo("033-572-3011");
        }

        @Test
        @DisplayName("번호만 오면 그대로 돌려준다")
        void 번호만_오면_그대로() {
            // 17,446 건은 이미 번호 하나만 옴
            assertThat(ValueCleaner.extractPhone("055-762-1234")).isEqualTo("055-762-1234");
        }

        @Test
        @DisplayName("번호가 없으면 null 이다")
        void 번호가_없으면_null() {
            // 전화번호 자리에 번호 아닌 것을 담을 이유가 없음
            assertThat(ValueCleaner.extractPhone("관광안내소로 문의")).isNull();
            assertThat(ValueCleaner.extractPhone(null)).isNull();
        }
    }

    @Nested
    @DisplayName("홈페이지")
    class Url {

        @Test
        @DisplayName("앵커 태그에서 주소만 남긴다")
        void 앵커에서_주소만() {
            // 채워진 865 건 중 385 건이 태그이고 나머지는 순수 주소라 두 형태가 섞여 옴
            String raw = "<a href=\"https://hangang.seoul.go.kr/www/contents/\" target=\"_blank\">한강공원</a>";
            assertThat(ValueCleaner.cleanUrl(raw))
                    .isEqualTo("https://hangang.seoul.go.kr/www/contents/");
        }

        @Test
        @DisplayName("순수 주소는 그대로 둔다")
        void 순수_주소는_그대로() {
            assertThat(ValueCleaner.cleanUrl("https://www.example.go.kr"))
                    .isEqualTo("https://www.example.go.kr");
        }

        @Test
        @DisplayName("href 가 없는 태그는 걷어낸다")
        void href_없는_태그() {
            assertThat(ValueCleaner.cleanUrl("<p>www.example.kr</p>")).isEqualTo("www.example.kr");
        }
    }

    @Nested
    @DisplayName("이미지 주소")
    class Image {

        @Test
        @DisplayName("http 를 https 로 맞춘다")
        void https_로_맞춘다() {
            // 같은 목록 안에서도 행마다 스킴이 갈림, http 로 오는 것이 417 건이었음
            // 맞추지 않으면 브라우저가 혼합 콘텐츠로 막아 사진이 안 뜸
            assertThat(ValueCleaner.forceHttps("http://tong.visitkorea.or.kr/img.jpg"))
                    .isEqualTo("https://tong.visitkorea.or.kr/img.jpg");
        }

        @Test
        @DisplayName("이미 https 면 그대로 둔다")
        void 이미_https_면_그대로() {
            assertThat(ValueCleaner.forceHttps("https://tong.visitkorea.or.kr/img.jpg"))
                    .isEqualTo("https://tong.visitkorea.or.kr/img.jpg");
        }

        @Test
        @DisplayName("비었으면 null 이다")
        void 비었으면_null() {
            assertThat(ValueCleaner.forceHttps(null)).isNull();
            assertThat(ValueCleaner.forceHttps("  ")).isNull();
        }
    }
}
