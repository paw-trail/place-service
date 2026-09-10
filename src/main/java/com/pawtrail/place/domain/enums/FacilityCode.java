package com.pawtrail.place.domain.enums;

/**
 * 장소의 편의시설입니다.
 *
 * 출처가 확인된 값만 둡니다.
 * 값이 없으면 화면에서 편의시설 섹션 자체를 표시하지 않습니다.
 *
 * 컬럼이 varchar(24) 이고 CHECK 가 없으므로
 * 값이 늘어도 마이그레이션 없이 이 enum 만 고치면 됩니다.
 */
public enum FacilityCode {

    // 문화정보원 「주차 가능여부」
    // Y 와 N 으로만 오는 정형 값이라 판별이 확실함
    PARKING,

    // 고캠핑 posblFcltyCl 의 "산책로"
    WALKING_TRAIL,

    // 고캠핑 sbrsCl 의 "놀이터"
    PLAYGROUND,

    // 고캠핑 resveCl 의 "온라인실시간예약"
    RESERVATION

    // 명세에 있던 EMERGENCY_24H 는 넣지 않았음
    // 인허가 데이터에 24시나 영업시간 컬럼이 없고
    // 사업장명의 "24" 로 채우면 양방향으로 틀리는데 검증할 방법이 없음
    // 응급 상황에서 틀리면 대가가 가장 큰 자리라 "판정 없음" 을 택했음
    //
    // OUTDOOR_SEAT 도 채울 값이 없어 넣지 않았음
    // 화면 필터를 뺄지 문구를 바꿀지가 정해지면 되살아남
}
