package com.pawtrail.place.domain.rule;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 주소를 매칭에 쓸 수 있는 형태로 다듬습니다.
 *
 * address_normalized 는 병합 일 순위 키입니다.
 * 이 값이 틀리면 병합이 통째로 틀리는데, 오류가 나지 않아 알아채기 어렵습니다.
 *
 * 결과 형태는 시도 + 구분자 + 나머지입니다.
 *   인천광역시 중구 마시란로 118  ->  인천|중구마시란로118
 *
 * 시도를 따로 두는 이유는 나머지가 같아도 시도가 다르면 다른 장소이기 때문입니다.
 * 구분자를 두는 이유는 시도 경계를 눈으로 볼 수 있게 하기 위해서입니다.
 */
public final class AddressNormalizer {

    private static final Pattern PAREN = Pattern.compile("[(（][^)）]*[)）]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * 행정 구역 개편으로 소스마다 다르게 오는 시군구입니다.
     *
     * 영종구는 인천 중구에서 갈라져 나온 구입니다.
     * 공사는 개편을 반영해 영종구로 주고 문화정보원은 아직 중구로 줍니다.
     *
     * 좌표로는 붙지 않습니다.
     * 영종도 마시안해변 두 행이 151m 떨어져 있어 임계값 100m 를 넘습니다.
     * 그래서 주소 쪽에서 맞춰야 합니다.
     *
     * 적재본에 실제로 걸린 한 줄만 둡니다.
     * 미리 조사해 채우면 쓰지도 않는 항목이 쌓이고 맞는지 검증할 방법도 없습니다.
     * 같은 종류가 나오면 그때 한 줄 더 넣습니다.
     */
    private static final Map<String, String> DISTRICT_ALIAS = Map.of(
            "영종구", "중구"
    );

    private AddressNormalizer() {
    }

    /**
     * 정규화한 주소를 만듭니다.
     *
     * 도로명이 있으면 도로명을, 없으면 지번을 씁니다.
     * 문화정보원은 지번을 100% 채우고 공사 계열은 0% 이므로 폴백이 실제로 쓰입니다.
     *
     * sidoFallback 은 주소 첫 토큰으로 시도를 못 찾았을 때 씁니다.
     * 고캠핑의 doNm 과 문화정보원의 시도 명칭이 그것이며,
     * 주소가 "영덕군 남정면..." 처럼 시도 없이 오는 행에서 값이 멀쩡했습니다.
     * 예외 목록이 아니라 폴백이라 새로운 오염이 와도 같은 규칙으로 통과합니다.
     *
     * 시도를 끝내 못 찾으면 null 을 돌려줍니다.
     * 시도 없이 나머지만 담으면 다른 시도의 같은 도로명과 붙을 수 있어 위험합니다.
     */
    public static String normalize(String roadAddress, String jibunAddress, String sidoFallback) {
        String source = pick(roadAddress, jibunAddress);
        if (source == null) {
            return null;
        }

        // 괄호를 통째로 버립니다
        // 도로명 주소 뒤에 붙는 법정동 표기라 소스마다 있고 없고가 갈립니다
        //   인천광역시 영종구 마시란로 118 (덕교동)
        String cleaned = PAREN.matcher(source).replaceAll(" ").trim();
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        Resolved resolved = resolveSido(cleaned, sidoFallback);
        if (resolved == null) {
            return null;
        }

        String rest = applyDistrictAlias(resolved.rest());
        rest = WHITESPACE.matcher(rest).replaceAll("");
        if (rest.isEmpty()) {
            return null;
        }

        return resolved.sido().canonical() + "|" + rest;
    }

    /**
     * 이 주소의 시도를 찾습니다. 코드가 아니라 시도 자체를 돌려줍니다.
     *
     * place.sido_code 를 채울 때도 이 결과를 씁니다.
     */
    public static Sido resolveSidoOnly(String roadAddress, String jibunAddress,
                                       String sidoFallback) {
        String source = pick(roadAddress, jibunAddress);
        if (source == null) {
            return Sido.fromName(sidoFallback);
        }
        String cleaned = PAREN.matcher(source).replaceAll(" ").trim();
        Resolved resolved = resolveSido(cleaned, sidoFallback);
        return resolved == null ? null : resolved.sido();
    }

    /**
     * 이 주소의 시군구 이름을 뽑습니다. place.sigungu_name 을 채울 때 씁니다.
     *
     * 시도를 떼어 낸 나머지의 첫 토큰이 시 · 군 · 구로 끝나면 그 이름입니다.
     * 시 다음 토큰이 구로 끝나면 둘을 붙입니다. 일반구가 있는 시입니다.
     *   경기도 고양시 덕양구 동세로 19     ->  고양시 덕양구
     *   서울특별시 종로구 창경궁로 261     ->  종로구
     *   세종특별자치시 한누리대로 2012     ->  null
     *
     * 세종은 시군구가 없는 단층 자치단체라 null 이 맞는 값입니다.
     * 문화정보원도 세종 행의 시군구 명칭을 비워 둡니다.
     *
     * 개편 별칭(영종구 -> 중구)은 쓰지 않습니다.
     * 그 표는 병합 매칭에서 같은 주소를 같게 보려는 것이고,
     * 이 값은 사람이 고르는 지역 이름이라 주소에 적힌 대로 둡니다.
     * 그래서 옛 이름과 새 이름이 함께 나올 수 있습니다.
     *
     * 규칙은 원본 파일과 대조했습니다.
     * 문화정보원 CSV 에서 중복을 뺀 23,925 행 가운데 소스의 시군구 명칭과 23,780 행이 같았고 다른 행은 없었습니다.
     * 뽑지 못한 145 행은 전부 세종이었습니다.
     *
     * 시도를 끝내 못 찾으면 null 입니다.
     * 시도 없이 시군구만 담으면 다른 시도의 같은 이름(중구 · 동구)과 섞입니다.
     */
    public static String resolveSigunguName(String roadAddress, String jibunAddress,
                                            String sidoFallback) {
        String source = pick(roadAddress, jibunAddress);
        if (source == null) {
            return null;
        }
        String cleaned = PAREN.matcher(source).replaceAll(" ").trim();
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        Resolved resolved = resolveSido(cleaned, sidoFallback);
        if (resolved == null || resolved.rest().isEmpty()) {
            return null;
        }

        String[] tokens = resolved.rest().split(" ");
        String first = tokens[0];
        if (!isDistrict(first)) {
            return null;
        }
        // 일반구가 있는 시는 구까지 붙여야 한 지역이 됩니다
        // 고양시에는 덕양구 · 일산동구 · 일산서구가 있어 시만 담으면 세 구가 한 이름으로 뭉칩니다
        if (first.endsWith("시") && tokens.length > 1 && isDistrict(tokens[1])
                && tokens[1].endsWith("구")) {
            return first + " " + tokens[1];
        }
        return first;
    }

    // 시 · 군 · 구로 끝나는 두 글자 이상의 토큰인지 봅니다
    // 한 글자짜리는 행정 구역 이름이 될 수 없습니다
    private static boolean isDistrict(String token) {
        return token.length() >= 2
                && (token.endsWith("시") || token.endsWith("군") || token.endsWith("구"));
    }

    private static String pick(String roadAddress, String jibunAddress) {
        if (roadAddress != null && !roadAddress.isBlank()) {
            return roadAddress.trim();
        }
        if (jibunAddress != null && !jibunAddress.isBlank()) {
            return jibunAddress.trim();
        }
        return null;
    }

    /**
     * 시도를 떼어내고 나머지를 남깁니다.
     *
     * 순서가 둘입니다.
     *   시도 표기가 맞으면 그만큼 떼어냅니다
     *     통합 명칭이 맞았으면 남은 부분의 시군구를 보고 전남과 광주로 되돌립니다
     *   맞는 표기가 없으면 소스가 준 시도 필드를 앞에 붙인 것으로 봅니다
     *
     * 통합 명칭을 따로 검사하지 않는 것이 중요합니다.
     * Sido 가 통합 명칭까지 한 목록에 담아 길이 내림차순으로 찾으므로
     * "전남" 이 "전남광주통합특별시" 보다 먼저 맞는 일이 구조적으로 없습니다.
     */
    private static Resolved resolveSido(String address, String sidoFallback) {
        Sido.Prefix prefix = Sido.matchPrefix(address);
        if (prefix != null) {
            String rest = address.substring(prefix.length()).trim();
            // 통합 명칭이면 시군구를 보고 전남과 광주로 되돌립니다
            Sido sido = prefix.isMerged() ? splitMerged(rest) : prefix.sido();
            return new Resolved(sido, rest);
        }

        // 주소에 시도가 없는 행입니다
        // 소스가 시도를 따로 주므로 그것을 씁니다, 주소 전체가 나머지가 됩니다
        Sido fallback = Sido.fromName(sidoFallback);
        return fallback == null ? null : new Resolved(fallback, address);
    }

    /**
     * 통합 명칭을 전남과 광주로 되돌립니다.
     *
     * 나머지의 첫 토큰이 광주의 자치구 다섯 중 하나면 광주이고 아니면 전남입니다.
     *   전남광주통합특별시 남구 사직길 49   ->  광주
     *   전남광주통합특별시 담양군 ...       ->  전남
     *
     * 공식 행정 구역과 어긋나는 것은 압니다.
     * 소스가 통합을 반영했는데 우리가 되돌리는 것입니다.
     * 그래도 이렇게 하는 이유는 이 컬럼이 매칭용이고,
     * 통합 명칭으로 두면 공사 365 건이 문화정보원 922 건과 영영 붙지 않기 때문입니다.
     * 화면의 지역 필터도 시도 열일곱 기준이라 통합 칸이 없습니다.
     *
     * 원본은 사라지지 않습니다.
     * ingest 의 raw_document 가 소스가 준 주소를 그대로 갖고 있습니다.
     */
    private static Sido splitMerged(String rest) {
        if (rest.isEmpty()) {
            return Sido.JEONNAM;
        }
        String firstToken = rest.split("\\s+")[0];
        return Sido.GWANGJU_GU.contains(firstToken) ? Sido.GWANGJU : Sido.JEONNAM;
    }

    private static String applyDistrictAlias(String rest) {
        if (rest.isEmpty()) {
            return rest;
        }
        String[] tokens = rest.split("\\s+", 2);
        String replaced = DISTRICT_ALIAS.get(tokens[0]);
        if (replaced == null) {
            return rest;
        }
        return tokens.length == 1 ? replaced : replaced + " " + tokens[1];
    }

    private record Resolved(Sido sido, String rest) {
    }
}
