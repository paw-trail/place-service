package com.pawtrail.place.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 좌표 정규화 규칙을 고정합니다.
 */
class CoordinateNormalizerTest {

    @Test
    @DisplayName("소수 일곱 자리로 반올림한다")
    void 일곱_자리로_반올림() {
        // 컬럼이 numeric(10,7) 이라 데이터베이스가 어차피 반올림함
        // 미리 자르는 이유는 정밀도가 아니라 geom 정합임
        // 엔티티가 반올림 전 값으로 geom 을 만들면 저장된 좌표와 다른 값에서 나온 것이 됨
        var result = CoordinateNormalizer.normalize("37.5693544270", "127.0068155826");
        assertThat(result.usable()).isTrue();
        assertThat(result.lat()).isEqualTo(new BigDecimal("37.5693544"));
        assertThat(result.lon()).isEqualTo(new BigDecimal("127.0068156"));
    }

    @Test
    @DisplayName("버리지 않고 반올림한다")
    void 버리지_않고_반올림() {
        // PostgreSQL 이 numeric 에 넣을 때 하는 것이 반올림이므로 같아야 함
        // 버리면 다시 적재할 때 값이 흔들림
        var result = CoordinateNormalizer.normalize("37.00000005", "127.00000005");
        assertThat(result.lat()).isEqualTo(new BigDecimal("37.0000001"));
    }

    @Test
    @DisplayName("자릿수가 모자라면 채운다")
    void 자릿수를_채운다() {
        // 저장되는 형태를 그대로 만들어 두어야 비교가 어긋나지 않음
        var result = CoordinateNormalizer.normalize("37.5", "127.0");
        assertThat(result.lat()).isEqualTo(new BigDecimal("37.5000000"));
    }

    @Test
    @DisplayName("대한민국 밖이면 못 쓴다고 답한다")
    void 한국_밖은_못_씀() {
        // 오산반려동물테마파크와 기흥레스피아호수공원이 똑같이 이 값으로 옴
        // 필리핀 앞바다이며 소스의 오류값으로 보임
        //
        // 그대로 담으면 둘이 정확히 같은 값이라 좌표 근접 판정에서 거리가 0m 임
        // 같은 오류값을 가진 행이 더 들어오면 서로 다른 장소들이 한 덩어리로 뭉침
        var result = CoordinateNormalizer.normalize("19.69442748", "117.9925662504");
        assertThat(result.usable()).isFalse();
        assertThat(result.lat()).isNull();
        assertThat(result.lon()).isNull();
    }

    @Test
    @DisplayName("경계 안은 받는다")
    void 경계_안은_받는다() {
        // 마라도와 독도, 백령도가 들어오는지 봄
        assertThat(CoordinateNormalizer.normalize("33.1", "126.27").usable()).isTrue();
        assertThat(CoordinateNormalizer.normalize("37.24", "131.86").usable()).isTrue();
        assertThat(CoordinateNormalizer.normalize("37.96", "124.63").usable()).isTrue();
    }

    @Test
    @DisplayName("비었거나 숫자가 아니면 못 쓴다고 답한다")
    void 없거나_숫자가_아니면_못_씀() {
        // 부르는 쪽이 하는 일이 "지오코딩으로 넘긴다" 로 같아 구분하지 않음
        assertThat(CoordinateNormalizer.normalize(null, "127.0").usable()).isFalse();
        assertThat(CoordinateNormalizer.normalize("", "127.0").usable()).isFalse();
        assertThat(CoordinateNormalizer.normalize("없음", "127.0").usable()).isFalse();
    }
}
