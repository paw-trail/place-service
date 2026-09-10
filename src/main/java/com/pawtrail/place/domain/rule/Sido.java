package com.pawtrail.place.domain.rule;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시도 열일곱 곳과 그 표기들입니다.
 *
 * 소스마다 같은 지역을 다르게 부릅니다.
 * 적재본에서 주소 첫 토큰이 공사 스물세 종, 고캠핑 서른네 종, 문화정보원 열일곱 종이었습니다.
 * 강원 하나만 해도 강원특별자치도 · 강원도 · 강원 세 갈래로 왔습니다.
 *
 * 이것을 통일하지 않으면 같은 장소가 다른 주소로 보여 병합이 되지 않습니다.
 * 시도명 표준화 하나가 빠지면 주소 일치 쌍이 스물일곱 개 줄어듭니다.
 */
public enum Sido {

    SEOUL("11", "서울", "서울특별시", "서울시"),
    BUSAN("26", "부산", "부산광역시", "부산시"),
    DAEGU("27", "대구", "대구광역시", "대구시"),
    INCHEON("28", "인천", "인천광역시", "인천시"),
    GWANGJU("29", "광주", "광주광역시", "광주시"),
    DAEJEON("30", "대전", "대전광역시", "대전시"),
    ULSAN("31", "울산", "울산광역시", "울산시"),
    SEJONG("36", "세종", "세종특별자치시", "세종시"),
    GYEONGGI("41", "경기", "경기도"),
    GANGWON("51", "강원", "강원특별자치도", "강원도"),
    CHUNGBUK("43", "충북", "충청북도"),
    CHUNGNAM("44", "충남", "충청남도"),
    JEONBUK("52", "전북", "전북특별자치도", "전라북도"),
    JEONNAM("46", "전남", "전라남도"),
    GYEONGBUK("47", "경북", "경상북도"),
    GYEONGNAM("48", "경남", "경상남도"),
    JEJU("50", "제주", "제주특별자치도", "제주도");

    // 통합 명칭입니다
    //
    // 공사 계열이 전남과 광주를 하나로 합쳐 이 이름으로 줍니다, 코드는 12 입니다.
    // 문화정보원은 여전히 전라남도와 광주광역시로 따로 줍니다.
    //
    // 이 값을 그대로 두면 공사 365 건이 문화정보원 922 건과 영영 붙지 않습니다.
    // 그래서 시군구를 보고 둘로 되돌립니다, AddressNormalizer 를 보십시오.
    public static final String MERGED_JEONNAM_GWANGJU = "전남광주통합특별시";

    // 광주광역시의 자치구 다섯입니다
    //
    // 통합 명칭을 되돌릴 때 이 다섯이면 광주이고 아니면 전남입니다.
    // 시군구 이름을 전부 알 필요가 없다는 것이 이 방식을 고른 근거였습니다.
    public static final Set<String> GWANGJU_GU =
            Set.of("동구", "서구", "남구", "북구", "광산구");

    private final String code;
    private final String canonical;
    private final List<String> variants;

    Sido(String code, String canonical, String... variants) {
        this.code = code;
        this.canonical = canonical;
        this.variants = List.of(variants);
    }

    /**
     * 법정동 코드의 시도 부분입니다. 두 자리입니다.
     */
    public String code() {
        return code;
    }

    /**
     * 정규화 주소에 쓰는 짧은 이름입니다.
     */
    public String canonical() {
        return canonical;
    }

    // 표기 -> 시도
    //
    // 긴 표기부터 찾아야 합니다.
    // "강원" 을 먼저 맞추면 "강원특별자치도" 가 "특별자치도" 를 남긴 채 잘립니다.
    private static final List<Map.Entry<String, Sido>> BY_LENGTH_DESC =
            java.util.Arrays.stream(values())
                    .flatMap(sido -> java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(sido.canonical),
                                    sido.variants.stream())
                            .map(name -> Map.entry(name, sido)))
                    .sorted((a, b) -> b.getKey().length() - a.getKey().length())
                    .toList();

    // 코드 -> 시도
    private static final Map<String, Sido> BY_CODE =
            java.util.Arrays.stream(values())
                    .collect(java.util.stream.Collectors.toMap(Sido::code, s -> s));

    /**
     * 주소가 시도 표기로 시작하면 그 시도와 표기 길이를 함께 돌려줍니다.
     *
     * 길이를 함께 주는 이유는 부르는 쪽이 그만큼 잘라내야 하기 때문입니다.
     * 시도만 돌려주면 어느 표기가 맞았는지 알 수 없어 다시 찾게 됩니다.
     *
     * 긴 표기부터 맞춰 봅니다.
     * "강원" 을 먼저 맞추면 "강원특별자치도" 에서 "특별자치도" 가 남습니다.
     *
     * 통합 명칭은 여기서 다루지 않습니다. 시군구를 함께 봐야 하기 때문입니다.
     */
    public static Prefix matchPrefix(String address) {
        if (address == null) {
            return null;
        }
        for (Map.Entry<String, Sido> entry : BY_LENGTH_DESC) {
            if (address.startsWith(entry.getKey())) {
                return new Prefix(entry.getValue(), entry.getKey().length());
            }
        }
        return null;
    }

    /**
     * 주소 앞에서 찾아낸 시도와 그 표기의 길이입니다.
     */
    public record Prefix(Sido sido, int length) {
    }

    /**
     * 표기 하나를 시도로 바꿉니다. 주소가 아니라 시도 필드 값에 씁니다.
     *
     * 고캠핑의 doNm 과 문화정보원의 시도 명칭이 이 형태로 옵니다.
     */
    public static Sido fromName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        for (Map.Entry<String, Sido> entry : BY_LENGTH_DESC) {
            if (entry.getKey().equals(trimmed)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 법정동 코드에서 시도를 찾습니다.
     *
     * 길이와 무관하게 앞 두 자리가 시도를 결정합니다.
     * 법정동 코드가 시도 2 + 시군구 3 + 읍면동 3 + 리 2 구조이기 때문입니다.
     * 소스가 다섯 자리를 주는 행이 실제로 있습니다, 세종의 36110 입니다.
     *
     * 통합 코드 12 는 여기서 답할 수 없습니다.
     * 전남인지 광주인지가 시군구에 달려 있어 null 을 돌려줍니다.
     */
    public static Sido fromCode(String code) {
        if (code == null || code.length() < 2) {
            return null;
        }
        return BY_CODE.get(code.substring(0, 2));
    }
}
