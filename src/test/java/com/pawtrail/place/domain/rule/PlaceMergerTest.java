package com.pawtrail.place.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.place.domain.enums.SourceType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 대표 소스 선정 규칙을 고정합니다.
 */
class PlaceMergerTest {

    @Test
    @DisplayName("순서가 앞서면 대표를 가져간다")
    void 앞서면_가져간다() {
        // 채움률에는 영향이 없고 충돌할 때의 품질에 영향이 있음
        // overview 가 충돌하는 경우가 94% 인데
        // 문화정보원 값은 "관광지" 같은 카테고리 라벨이고 공사 계열은 실제 소개문임
        assertThat(PlaceMerger.shouldTakeOver(SourceType.PET_TOUR, SourceType.CULTURE_CSV)).isTrue();
        assertThat(PlaceMerger.shouldTakeOver(SourceType.GOCAMPING, SourceType.CULTURE_CSV)).isTrue();
        assertThat(PlaceMerger.shouldTakeOver(SourceType.PET_TOUR, SourceType.GOCAMPING)).isTrue();
    }

    @Test
    @DisplayName("순서가 뒤면 가져가지 않는다")
    void 뒤면_안_가져간다() {
        assertThat(PlaceMerger.shouldTakeOver(SourceType.CULTURE_CSV, SourceType.PET_TOUR)).isFalse();
        assertThat(PlaceMerger.shouldTakeOver(SourceType.MOIS_VET, SourceType.CULTURE_CSV)).isFalse();
    }

    @Test
    @DisplayName("같은 소스면 먼저 들어온 것이 대표로 남는다")
    void 같은_소스면_그대로() {
        // 적재본에 같은 소스 안의 중복이 18 쌍 있음
        assertThat(PlaceMerger.shouldTakeOver(SourceType.PET_TOUR, SourceType.PET_TOUR)).isFalse();
    }

    @Test
    @DisplayName("null 이면 가져가지 않는다")
    void null_이면_안_가져간다() {
        assertThat(PlaceMerger.shouldTakeOver(null, SourceType.PET_TOUR)).isFalse();
        assertThat(PlaceMerger.shouldTakeOver(SourceType.PET_TOUR, null)).isFalse();
    }

    @Test
    @DisplayName("목록에서 가장 앞선 소스를 고른다")
    void 가장_앞선_것을_고른다() {
        // 소스 분리로 대표가 사라졌을 때 나머지 중 하나를 승격하는 자리에서 씀
        assertThat(PlaceMerger.choosePrimary(
                List.of(SourceType.CULTURE_CSV, SourceType.PET_TOUR, SourceType.GOCAMPING)))
                .isEqualTo(SourceType.PET_TOUR);
        assertThat(PlaceMerger.choosePrimary(List.of(SourceType.MOIS_VET, SourceType.CULTURE_CSV)))
                .isEqualTo(SourceType.CULTURE_CSV);
    }

    @Test
    @DisplayName("목록이 비면 null 이다")
    void 비면_null() {
        assertThat(PlaceMerger.choosePrimary(List.of())).isNull();
        assertThat(PlaceMerger.choosePrimary(null)).isNull();
    }
}
