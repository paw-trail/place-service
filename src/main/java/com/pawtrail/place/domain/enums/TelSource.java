package com.pawtrail.place.domain.enums;

/**
 * 전화번호가 어느 소스에서 왔는지입니다.
 *
 * 소스마다 채움률이 크게 달라 보완 순서를 판단하는 데 씁니다.
 * SourceType 을 그대로 쓰지 않는 이유는 카카오 지오코딩으로 보완한 값이
 * 데이터셋이 아니라 외부 API 에서 오기 때문입니다.
 */
public enum TelSource {

    // 공사 상세 응답의 infocenter
    // 목록과 공통 응답은 전 계열 0% 이고 상세에서만 나옴
    PET_TOUR,

    GOCAMPING,

    CULTURE_CSV,

    MOIS_VET,

    // 카카오 로컬 API 로 보완한 값
    KAKAO,

    // 관리자가 직접 넣은 값
    //
    // PATCH /admin/places/{placeId} 로 번호를 고치면 이 값이 됨
    // 소스가 준 값이 아니므로 SourceType 과 짝이 맞지 않는 유일한 값임
    //
    // * null 로 두지 않는 이유
    //   null 은 "소스가 번호를 안 줬다" 는 뜻으로 이미 쓰이고 있음
    //   관리자가 넣은 값을 거기 섞으면 둘을 가를 수 없게 됨
    //   policy 가 관리자 정정을 pet_policy_source 의 MANUAL 티어로 남기는 것과 같은 축임
    MANUAL
}
