package com.pawtrail.place.domain.rule;

import com.pawtrail.place.domain.enums.FacilityCode;
import com.pawtrail.place.domain.enums.SourceType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 소스가 준 원본 값에서 편의시설을 판별합니다.
 *
 * 판별을 place 가 하는 이유는 place_type 매핑을 여기서 하기 때문입니다.
 * 편의시설만 ingest 가 판별하면 같은 종류의 규칙이 두 레포로 갈립니다.
 *
 * 출처가 확인된 값만 채웁니다.
 * 명세에 있던 EMERGENCY_24H 는 인허가 데이터에 24시나 영업시간 컬럼이 없어 뺐고,
 * OUTDOOR_SEAT 은 네 소스 어디에도 해당하는 값이 없습니다.
 */
public final class FacilityResolver {

    // 주차 가능을 뜻하는 말입니다
    //
    // 공사 값이 자유 텍스트라 형태가 여럿입니다
    //   가능 · 있음 · 가능요금 (무료) · 가능(10대 이상) · 가능(무료)
    //   가능<br>요금 (무료)
    private static final Pattern PARKING_AVAILABLE = Pattern.compile("가능|있음|^Y$");

    // 주차 불가를 뜻하는 말입니다
    //
    // "불가능" 과 "불가" 가 섞여 오므로 앞에서 먼저 걸러야 합니다
    // "불가능" 안에 "가능" 이 들어 있어 순서가 뒤집히면 정반대로 판정됩니다
    //
    // 앞머리 고정을 걸지 않습니다
    // 값이 "불가" 로 시작한다는 보장이 없어 "주차 불가능" 같은 형태가 오면
    // 이 무늬에 안 걸리고 아래 "가능" 에 걸려 정반대가 됩니다
    // 적재본에는 그 형태가 없었으나 자유 텍스트라 언제든 올 수 있습니다
    //
    // N 만 고정을 두는 이유는 한 글자라 다른 말에 섞여 들어갈 수 있기 때문입니다
    private static final Pattern PARKING_UNAVAILABLE = Pattern.compile("불가|^N$");

    // 고캠핑이 값을 쉼표나 빗금으로 이어 붙여 줍니다
    private static final Pattern DELIMITER = Pattern.compile("[,/]");

    // 고캠핑의 산책로입니다
    //
    // 두 필드에 따로 있어 합칩니다
    //   posblFcltyCl   야영장 주변에 있는 것   1,247건
    //   sbrsCl         야영장이 갖춘 것        708건
    // 뜻이 미묘하게 다르지만 "반려동물과 산책할 데가 있나" 에는 둘 다 예스입니다
    // 필드가 갈린 것은 소스 사정이지 우리 화면의 구분이 아닙니다
    private static final String WALKING_TRAIL = "산책로";

    // 고캠핑 부대시설의 놀이터입니다
    //
    // posblFcltyCl 의 "어린이놀이시설" 297건은 합치지 않습니다
    // 이름에 어린이가 명시돼 있어 반려동물 서비스에서 묶으면
    // 사용자가 강아지 놀이터로 오해합니다
    private static final String PLAYGROUND = "놀이터";

    // 고캠핑 예약 구분입니다
    // 전화와 현장은 온라인 예약이 아니므로 이 값만 봅니다
    private static final String ONLINE_RESERVATION = "온라인실시간예약";

    private FacilityResolver() {
    }

    /**
     * 편의시설 코드를 뽑습니다.
     *
     * 순서를 유지하는 집합을 씁니다.
     * 중복을 없애면서도 결과가 매번 같은 순서로 나와야
     * 두 번 적재했을 때 place_facility 행이 흔들리지 않습니다.
     */
    public static List<FacilityCode> resolve(SourceType source,
                                             String parking,
                                             String posblFcltyCl,
                                             String sbrsCl,
                                             String resveCl) {

        Set<FacilityCode> codes = new LinkedHashSet<>();

        if (hasParking(parking)) {
            codes.add(FacilityCode.PARKING);
        }
        if (containsToken(posblFcltyCl, WALKING_TRAIL) || containsToken(sbrsCl, WALKING_TRAIL)) {
            codes.add(FacilityCode.WALKING_TRAIL);
        }
        if (containsToken(sbrsCl, PLAYGROUND)) {
            codes.add(FacilityCode.PLAYGROUND);
        }
        if (containsToken(resveCl, ONLINE_RESERVATION)) {
            codes.add(FacilityCode.RESERVATION);
        }

        return List.copyOf(codes);
    }

    /**
     * 주차가 가능한지 봅니다.
     *
     * 불가를 먼저 보는 것이 중요합니다.
     * "불가능" 안에 "가능" 이 들어 있어 순서가 뒤집히면 정반대로 판정됩니다.
     *
     * 판단할 수 없으면 거짓입니다.
     * 값이 없다는 것이 "주차가 없다" 는 뜻은 아니지만,
     * 편의시설은 있는 것만 표시하고 없으면 섹션 자체를 안 그리므로
     * 모르는 것을 있다고 하는 쪽이 더 나쁩니다.
     */
    private static boolean hasParking(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String text = value.trim();
        if (PARKING_UNAVAILABLE.matcher(text).find()) {
            return false;
        }
        return PARKING_AVAILABLE.matcher(text).find();
    }

    /**
     * 쉼표나 빗금으로 이어 붙은 값에 그 항목이 있는지 봅니다.
     *
     * 통째로 contains 하지 않는 이유는 부분 일치를 피하기 위해서입니다.
     * "어린이놀이시설" 에 "놀이터" 가 들어 있지는 않지만,
     * 소스가 값을 늘리면 그런 조합이 생길 수 있습니다.
     */
    private static boolean containsToken(String value, String token) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String part : DELIMITER.split(value)) {
            if (token.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }
}
