package com.pawtrail.place.application.dto.output;

/**
 * 적재 결과 건수입니다.
 *
 * 담지 않으면 만 칠천 건을 보냈는데 몇 건이 빠졌는지 아무도 모릅니다.
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
        int pendingFailed) {
}
