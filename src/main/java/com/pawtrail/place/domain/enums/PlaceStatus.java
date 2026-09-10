package com.pawtrail.place.domain.enums;

/**
 * 장소의 영업 상태입니다.
 *
 * 폐업한 장소도 행을 지우지 않고 CLOSED 로 표시합니다.
 * 지우면 즐겨찾기 · 방문 기록 · 후기의 참조가 끊깁니다.
 */
public enum PlaceStatus {

    ACTIVE,

    // 폐업했거나 더 이상 노출되지 않음
    //
    // 이 값으로 바꿀 신호는 공사 목록의 showflag 가 해제되는 것 하나뿐임
    // 수집이 areaBasedList2 가 아니라 petTourSyncList2 를 쓰는 이유가 그것임
    // 전자만 쓰면 비표출로 바뀐 콘텐츠가 목록에서 그냥 사라져
    // "없어진 것" 과 "원래 없던 것" 을 구분할 수 없음
    CLOSED,

    // 소스가 상태를 알려주지 않음
    UNKNOWN
}
