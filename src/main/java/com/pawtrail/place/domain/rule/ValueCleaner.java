package com.pawtrail.place.domain.rule;

import java.util.regex.Pattern;

/**
 * 소스가 준 문자열에서 쓸 값을 뽑습니다.
 *
 * 세 가지가 여기 모여 있습니다. 전화번호, 홈페이지, 이미지 주소입니다.
 * 셋 다 "소스가 준 문자열을 그대로는 못 쓴다" 는 같은 문제이므로 한 자리에 둡니다.
 * 나뉘어 있으면 같은 종류의 규칙이 두 단계에 생깁니다.
 */
public final class ValueCleaner {

    // 전화번호 한 개를 뽑습니다
    //
    // 두 형태를 받습니다
    //   세 덩어리   지역번호 2~4 + 국번 3~4 + 뒷자리 4     02-2148-4161 · 031-8025-3300
    //   두 덩어리   대표번호 4 + 4                       1644-4001 · 1577-0880
    //
    // 대표번호를 빠뜨리면 적재본에서 314 건을 놓칩니다
    // 세 덩어리를 먼저 쓰는 이유는 그것이 더 긴 형태이기 때문입니다
    // 두 덩어리를 앞에 두면 02-2148-4161 에서 2148-4161 만 잘라 갑니다
    //
    // 뒷자리가 세 자리인 번호는 받지 않습니다
    // 적재본에 그런 형태가 없고, 있다면 소스가 값을 잘라 보낸 것이라
    // 담으면 걸리지 않는 번호가 화면에 뜹니다
    private static final Pattern PHONE =
            Pattern.compile("\\d{2,4}-\\d{3,4}-\\d{4}|\\d{4}-\\d{4}");

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    // 앵커 태그의 href 값입니다
    // 따옴표는 큰따옴표와 작은따옴표를 모두 받습니다
    private static final Pattern HREF =
            Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ValueCleaner() {
    }

    /**
     * 안내 문구에서 전화번호 하나를 뽑습니다.
     *
     * 공사의 infocenter 는 번호가 아니라 안내 문구입니다.
     * 기관명과 번호가 함께 오고 안내처가 여럿이면 줄바꿈으로 이어붙습니다.
     *   "북촌마을안내소 02-2148-4161\n서울 종로구청 관광체육과 02-2148-1858"
     * br 태그까지 섞여 오는 행이 열두 개 있습니다.
     *
     * 컬럼이 varchar(30) 이라 문구를 담을 수 없기도 하지만,
     * 폭을 넓히지 않은 진짜 이유는 이 값의 뜻이 "전화번호" 이기 때문입니다.
     * 화면에서 누르면 전화가 걸려야 하는데 문구가 들어가면 tel 링크를 만들 수 없고
     * 파싱이 프론트로 넘어가 화면마다 반복됩니다.
     *
     * 첫 번호를 고르는 것이 맞는 이유는 실물에서 앞이 그 장소 직통이고
     * 뒤가 관공서나 상급 기관이기 때문입니다.
     *
     * 안내 문구 전체가 사라지지는 않습니다.
     * ingest 의 raw_document 가 원문을 갖고 있고
     * display_body 의 문의처 항목에 그대로 들어가 있어 원문 보기에서 볼 수 있습니다.
     *
     * 번호를 못 찾으면 null 입니다.
     * 전화번호 자리에 번호 아닌 것을 담을 이유가 없습니다.
     */
    public static String extractPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 태그를 먼저 걷어냅니다
        // "033-572-301<br>" 처럼 태그가 번호에 붙어 있으면 자릿수 판정이 어긋납니다
        String text = HTML_TAG.matcher(raw).replaceAll(" ");
        var matcher = PHONE.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 홈페이지 값에서 주소만 남깁니다.
     *
     * 공사는 앵커 태그를 통째로 줍니다.
     *   <a href="https://hangang.seoul.go.kr/..." target="_blank">한강공원</a>
     * 채워진 865 건 중 385 건이 태그이고 나머지는 순수 주소라 두 형태가 섞여 옵니다.
     *
     * href 가 있으면 그 값을 쓰고 없으면 태그만 걷어냅니다.
     * 태그를 그대로 담으면 화면에 마크업이 그대로 뜨거나 프론트가 또 벗겨야 합니다.
     */
    public static String cleanUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();

        var matcher = HREF.matcher(text);
        if (matcher.find()) {
            return blankToNull(matcher.group(1).trim());
        }

        String stripped = HTML_TAG.matcher(text).replaceAll(" ");
        stripped = WHITESPACE.matcher(stripped).replaceAll(" ").trim();
        return blankToNull(stripped);
    }

    /**
     * 이미지 주소를 https 로 맞춥니다.
     *
     * 공사의 firstimage 는 같은 목록 안에서도 행마다 http 와 https 가 섞여 옵니다.
     * 적재본에서 http 로 오는 것이 417 건이었습니다.
     *
     * 맞추지 않으면 브라우저가 혼합 콘텐츠로 막아 사진이 안 뜹니다.
     * 우리 화면이 https 로 서비스되기 때문입니다.
     *
     * 같은 서버가 두 스킴을 모두 받는 것은 확인된 사실이 아닙니다.
     * 다만 http 로 두면 확실히 막히고 https 는 막힐 수도 있는 정도라
     * 실패 방향이 나은 쪽을 고릅니다.
     */
    public static String forceHttps(String raw) {
        String url = cleanUrl(raw);
        if (url == null) {
            return null;
        }
        if (url.regionMatches(true, 0, "http://", 0, "http://".length())) {
            return "https://" + url.substring("http://".length());
        }
        return url;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
