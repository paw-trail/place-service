package com.pawtrail.place.domain.enums;

/**
 * 반영 대기 값의 처리 상태입니다.
 *
 * 이름을 resolved 로 두지 않은 것은 값이 셋이라 boolean 처럼 읽히기 때문입니다.
 * report.status 와도 형태가 맞습니다.
 */
public enum PendingStatus {

    // 관리자가 아직 보지 않았음
    PENDING,

    // 승인됨. place 에 new_value 가 반영됨
    APPROVED,

    // 반려됨. place 는 그대로 둠
    REJECTED
}
