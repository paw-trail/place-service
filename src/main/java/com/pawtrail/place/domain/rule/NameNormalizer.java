package com.pawtrail.place.domain.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 장소 이름을 매칭에 쓸 수 있는 형태로 다듬습니다.
 *
 * 바깥을 전혀 모르는 순수 함수만 둡니다.
 * 값을 받아 값을 돌려주며 저장소도 외부 API 도 보지 않습니다.
 *
 * 이름 일치는 병합 네 단계 전부의 전제입니다.
 * 주소가 같고 이름이 다른 쌍이 적재본에 백 여 건 있었습니다.
 * 북촌8경과 북촌문화센터, 스타필드코엑스몰과 다이소코엑스몰점 같은 것들입니다.
 * 그래서 이름을 어떻게 다듬느냐가 병합 결과를 그대로 좌우합니다.
 */
public final class NameNormalizer {

    // 괄호 안 내용을 뽑습니다
    // 반각과 전각을 모두 받습니다, 소스마다 섞여 옵니다
    private static final Pattern PAREN = Pattern.compile("[(（]([^)）]{1,40})[)）]");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private NameNormalizer() {
    }

    /**
     * 매칭에 쓰는 정규화 이름을 만듭니다.
     *
     * 하는 일은 공백 제거 하나뿐입니다.
     *
     * 지점명을 지우지 않습니다.
     * 강남점과 홍대점은 서로 다른 장소이므로 지우면 거짓 병합이 됩니다.
     * 거짓 병합은 강남점 조건에 홍대점 원문을 붙이는 식으로 틀린 근거를 만드는데,
     * 근거 제시가 정체성인 서비스에 치명적입니다.
     *
     * 괄호도 지우지 않습니다.
     * 괄호를 뗀 형태는 별칭으로 따로 담습니다. 아래 extractAliases 를 보십시오.
     *
     * 공백만 제거해도 병합 쌍이 서른여덟 개 늘어납니다.
     * 소스마다 띄어쓰기가 달라 "이천 예스파크" 와 "이천예스파크" 가 갈리기 때문입니다.
     */
    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String result = WHITESPACE.matcher(name.trim()).replaceAll("");
        return result.isEmpty() ? null : result;
    }

    /**
     * 이 장소를 가리킬 수 있는 다른 문자열을 뽑습니다.
     *
     * 담는 것이 둘입니다.
     *   괄호 안 내용      송파나루공원(석촌호수)  ->  석촌호수
     *   괄호를 뺀 본명    포천아트밸리 (한탄강 유네스코 세계지질공원)  ->  포천아트밸리
     *
     * 괄호를 뺀 본명이 오히려 값어치가 큽니다.
     * 별칭 축을 넣었을 때 새로 붙는 다섯 쌍 중 넷이 본명 쪽에서 걸렸습니다.
     *
     * 걸러내지 않고 그대로 담습니다.
     * 괄호 안에는 별칭이 아닌 것이 절반 넘게 섞여 있습니다.
     *   지역 구분자   봉화산(서울) · 파로호(화천)      동명이인을 가르는 값이라 정반대로 작동함
     *   상위 분류     백운계곡(한탄강 유네스코 세계지질공원)  서로 다른 세 장소가 같은 값을 가짐
     *   의미 없는 것  (주)양촌여울체험캠프 -> 주 · 공둘 캠핑장(02) -> 02
     *
     * 그래도 지금 거르지 않는 이유는 이 값을 매칭 축으로 쓰지 않기로 했기 때문입니다.
     * 채워만 두면 나중에 규칙을 얹어 켤 수 있고 재적재가 필요 없습니다.
     * 켤 때 걸러야 하는 것은 시도 열일곱 토큰, 시군구 이름, 두 자 이하, 본명에 포함되는 것입니다.
     *
     * 순서를 지키고 중복을 없애기 위해 LinkedHashSet 을 씁니다.
     * 괄호가 여럿이면 앞에서부터 담고, 본명은 마지막에 담습니다.
     */
    public static List<String> extractAliases(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> aliases = new LinkedHashSet<>();

        Matcher matcher = PAREN.matcher(name);
        while (matcher.find()) {
            String inside = normalize(matcher.group(1));
            if (inside != null) {
                aliases.add(inside);
            }
        }

        // 괄호가 하나도 없으면 본명이 곧 정규화 이름이라 담을 이유가 없습니다
        if (aliases.isEmpty()) {
            return List.of();
        }

        String base = normalize(PAREN.matcher(name).replaceAll(""));
        if (base != null) {
            aliases.add(base);
        }

        // 정규화 이름과 같은 값은 뺍니다, 자기 자신을 별칭으로 두는 셈이 됩니다
        String normalized = normalize(name);
        aliases.remove(normalized);

        return new ArrayList<>(aliases);
    }
}
