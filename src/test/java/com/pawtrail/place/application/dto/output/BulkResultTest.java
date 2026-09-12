package com.pawtrail.place.application.dto.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.place.domain.enums.SourceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 적재 결과가 소스와 장소의 짝을 어떤 모양으로 담는지 검사합니다.
 *
 * 이 형태가 계약입니다.
 * 수집 서비스가 이 값으로 자기 표의 place_id 를 채우므로
 * 필드 이름이나 구조가 바뀌면 그쪽이 조용히 못 읽게 됩니다.
 *
 * 특히 지키려는 것이 둘입니다.
 * 짝을 맞추는 열쇠가 값 안에 들어 있어야 합니다.
 * 순서로 맞추면 어긋나도 오류가 나지 않고 엉뚱한 장소에 붙습니다.
 * 소스와 식별자를 따로 담아야 합니다.
 * 하나로 이어 붙이면 문화정보원의 조합 키에서 구분자가 값 안에 들어 있을 수 있습니다.
 */
class BulkResultTest {

    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");

    @Test
    @DisplayName("소스와 식별자를 따로 담는다")
    void 열쇠를_따로_담는다() {
        BulkResult.SourceLink link =
                new BulkResult.SourceLink(SourceType.PET_TOUR, "1039170", PLACE_A);

        assertThat(link.source()).isEqualTo(SourceType.PET_TOUR);
        assertThat(link.sourceId()).isEqualTo("1039170");
        assertThat(link.placeId()).isEqualTo(PLACE_A);
    }

    @Test
    @DisplayName("구분자가 들어 있는 식별자도 그대로 담는다")
    void 조합_키() {
        // 문화정보원은 이름과 주소를 막대로 이어 식별자를 만듦
        // 하나의 문자열로 이어 붙이는 형태였다면 이 값에서 깨짐
        BulkResult.SourceLink link =
                new BulkResult.SourceLink(SourceType.CULTURE_CSV, "A공원|서울 1", PLACE_B);

        assertThat(link.sourceId()).isEqualTo("A공원|서울 1");
    }

    @Test
    @DisplayName("건수와 매핑을 함께 담는다")
    void 결과_구성() {
        BulkResult result = new BulkResult(1, 1, 1, 0, 0, List.of(
                new BulkResult.SourceLink(SourceType.PET_TOUR, "1039170", PLACE_A),
                new BulkResult.SourceLink(SourceType.GOCAMPING, "100", PLACE_B)));

        // 건너뛴 한 건은 매핑에 없으므로 건수와 개수가 다름
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.links()).hasSize(2);
        assertThat(result.links())
                .extracting(BulkResult.SourceLink::placeId)
                .containsExactly(PLACE_A, PLACE_B);
    }

    @Test
    @DisplayName("붙은 것이 없으면 매핑이 빈다")
    void 빈_매핑() {
        BulkResult result = new BulkResult(0, 0, 2, 0, 0, List.of());

        assertThat(result.links()).isEmpty();
    }
}
