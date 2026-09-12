package com.pawtrail.place.application.dto.output;

import com.pawtrail.place.domain.enums.SourceType;
import java.util.List;
import java.util.UUID;

/**
 * 적재 결과입니다.
 *
 * 건수를 담지 않으면 만 칠천 건을 보냈는데 몇 건이 빠졌는지 아무도 모릅니다.
 * 소스와 장소의 짝을 담지 않으면 넘긴 쪽이 무엇이 어디로 갔는지 알 수 없습니다.
 */
public record BulkResult(
        // 새로 만든 장소
        int created,

        // 이미 있는 장소에 붙인 것
        int merged,

        // 적재하지 못한 것
        // 좌표도 주소도 없어 넣을 값이 없는 행입니다
        // 적재본에 네 건 있고 고캠핑 소스 자체가 주소를 주지 않았습니다
        int skipped,

        // 잠긴 장소라 대기 행으로 쌓은 것
        int pending,

        // 대기 행을 만들지 못한 것
        // 0 이 아니면 그 장소의 변경을 관리자가 못 보지만
        // 다음 수집에서 또 잡히므로 영구 손실은 아닙니다
        int pendingFailed,

        // 어느 소스 레코드가 어느 장소가 됐는지임
        //
        // ingest 가 이 값으로 자기 표의 place_id 를 채움
        // 건수만 돌려주면 넘긴 쪽은 무엇이 어디로 갔는지 알 수 없고
        // 그러면 원문 보기가 어느 장소의 원문인지 찾을 수 없음
        //
        // 부르는 쪽이 정한 값임
        // user 의 GET /internal/users?ids= 응답 필드를 부르는 쪽이 정한 것과 같은 자리임
        //
        // 건너뛴 것은 담기지 않음
        // 장소를 만들지 못해 알려 줄 식별자가 없고, 그 건수는 skipped 로 이미 드러남
        // 없는 것은 결과에서 빠진다는 점이 GET /internal/places?ids= 와 같음
        //
        // 잠긴 장소도 담김
        // 값을 바꾸지 못했을 뿐 이미 있는 장소에 붙은 것이라 식별자가 있음
        List<SourceLink> links) {

    /**
     * 소스 레코드 하나와 그것이 속한 장소입니다.
     *
     * 소스와 소스 식별자를 함께 담습니다.
     * 요청과 같은 순서로 돌려주고 부르는 쪽이 자리로 맞추게 하는 방법도 있으나,
     * 순서가 어긋나면 오류가 나지 않고 조용히 엉뚱한 장소에 붙습니다.
     * 값 안에 열쇠가 들어 있으면 그 일이 생기지 않습니다.
     *
     * 문자열 하나로 이어 붙이지 않습니다.
     * 문화정보원의 소스 식별자가 이름과 주소를 막대로 이은 조합 키라
     * 구분자를 무엇으로 두든 값 안에 그 글자가 들어 있을 수 있습니다.
     *
     * @param source   어느 데이터셋인지입니다.
     * @param sourceId 그 데이터셋 안에서의 식별자입니다. 보낸 값을 그대로 돌려줍니다.
     * @param placeId  그 레코드가 속한 장소입니다.
     */
    public record SourceLink(SourceType source, String sourceId, UUID placeId) {
    }
}
