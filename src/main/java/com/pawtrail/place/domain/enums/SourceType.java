package com.pawtrail.place.domain.enums;

/**
 * 장소를 가져온 데이터셋입니다.
 *
 * 규칙은 "데이터셋 하나 = 값 하나, 출처가 다르면 절대 합치지 않음" 입니다.
 * PET_TOUR 와 GOCAMPING 이 같은 한국관광공사인데도 값을 나눠 둔 것이 그 선례입니다.
 *
 * 사람이 읽는 이름(sourceLabel)은 코드 상수로 두고 컬럼으로 만들지 않습니다.
 * 기관 단위로 묶어 조회하는 화면이나 API 가 하나도 없기 때문입니다.
 */
public enum SourceType {

    // 한국관광공사 반려동물 동반여행
    PET_TOUR("한국관광공사"),

    // 한국관광공사 고캠핑
    GOCAMPING("한국관광공사 고캠핑"),

    // 문화정보원
    // 이 소스만 식별자 컬럼이 없어 ingest 가 시설명|지번주소 로 만들어 넘김
    CULTURE_CSV("문화정보원"),

    // 행정안전부 동물병원 인허가
    //
    // 이 소스만 raw_document 를 거치지 않고 bulk 로 바로 들어옴
    // 동반 조건 문구가 없어 재추출 재료도 원문보기 대상도 아니기 때문임
    //
    // 기관 약어를 넣은 이유는 다른 동물병원 데이터셋이 와도
    // QIA_VET · SEOUL_VET 처럼 자연히 갈리기 때문임
    MOIS_VET("행정안전부 동물병원 인허가");

    private final String label;

    SourceType(String label) {
        this.label = label;
    }

    /**
     * 사람에게 보여줄 출처 이름입니다.
     *
     * GET /places/{placeId}/documents 응답의 sourceLabel 이 이 값입니다.
     */
    public String label() {
        return label;
    }
}
