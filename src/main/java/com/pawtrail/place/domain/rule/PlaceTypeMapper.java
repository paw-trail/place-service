package com.pawtrail.place.domain.rule;

import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.SourceType;
import java.util.Map;
import java.util.Set;

/**
 * 소스의 원천 분류를 서비스 카테고리 아홉 종으로 옮깁니다.
 *
 * 소스가 넷인데 분류 체계가 제각각입니다.
 * 공사는 계층 코드를, 고캠핑은 업종 문자열을, 문화정보원은 자체 카테고리를 줍니다.
 *
 * 원천 분류는 place 의 lcls1 부터 lcls3 에 가공 없이 그대로 남습니다.
 * 그래야 이 표를 나중에 고쳐도 다시 옮길 수 있습니다.
 */
public final class PlaceTypeMapper {

    /**
     * 공사 분류를 중분류(lclsSystm2) 기준으로 옮깁니다.
     *
     * 대분류로 하지 않는 이유는 그 안이 갈리기 때문입니다.
     * VE 하나가 298 건인데 유원지와 공원, 벽화마을, 전망대, 미술관이 섞여 있습니다.
     * 통째로 공원으로 보내면 미술관 스물한 곳이 공원 칩에 들어갑니다.
     *
     * 실제 장소 이름을 전수로 확인하고 만든 표입니다.
     */
    private static final Map<String, PlaceType> BY_MIDDLE = Map.ofEntries(
            // 자연 — 산 · 해변 · 습지 · 숲 · 가로수길
            Map.entry("NA01", PlaceType.PARK),
            Map.entry("NA02", PlaceType.PARK),
            Map.entry("NA03", PlaceType.PARK),
            Map.entry("NA04", PlaceType.PARK),
            Map.entry("NA05", PlaceType.PARK),

            // 유원지 · 한강공원 · 근린공원 · 대공원 · 정원
            Map.entry("VE03", PlaceType.PARK),
            Map.entry("VE02", PlaceType.PARK),

            // 벽화마을 · 감천문화마을 · 커피거리 · 근대골목
            Map.entry("VE04", PlaceType.CULTURE),
            // 미술관 · 박물관 · 조각공원 · 전시관
            Map.entry("VE07", PlaceType.CULTURE),

            // 전망대 · 출렁다리 · 등대 · 스카이워크 · 분수
            Map.entry("VE01", PlaceType.LEISURE),

            // 관광단지 · 리조트
            Map.entry("VE05", PlaceType.STAY),

            // 복합 상업 공간 · 광장 · 도서관 · 수련장
            Map.entry("VE12", PlaceType.ETC),
            Map.entry("VE06", PlaceType.ETC),
            Map.entry("VE09", PlaceType.ETC),
            Map.entry("VE10", PlaceType.ETC),

            // 호텔 · 펜션 · 모텔 · 게스트하우스 · 한옥
            Map.entry("AC01", PlaceType.STAY),
            Map.entry("AC02", PlaceType.STAY),
            Map.entry("AC03", PlaceType.STAY),
            Map.entry("AC04", PlaceType.STAY),
            Map.entry("AC06", PlaceType.STAY),
            // 야영장 · 오토캠핑장
            Map.entry("AC05", PlaceType.CAMPING),

            // 고궁 · 사찰 · 유적 · 기념물
            Map.entry("HS01", PlaceType.CULTURE),
            Map.entry("HS02", PlaceType.CULTURE),
            Map.entry("HS03", PlaceType.CULTURE),
            Map.entry("HS04", PlaceType.CULTURE),

            // 유채꽃마을 · 매실농원 · 허브아일랜드 · 치유의 숲
            Map.entry("EX03", PlaceType.PARK),
            Map.entry("EX05", PlaceType.PARK),
            Map.entry("EX06", PlaceType.PARK),
            // 우주센터 · 유람선 · 케이블카 · 체험공방
            Map.entry("EX07", PlaceType.LEISURE),
            Map.entry("EX02", PlaceType.LEISURE),

            // 카페 · 젤라또 · 티룸
            Map.entry("FD05", PlaceType.CAFE),
            Map.entry("FD02", PlaceType.CAFE),
            // 횟집 · 삼겹살 · 메밀촌
            Map.entry("FD01", PlaceType.RESTAURANT),

            // 낚시 · 수상스키 · 래프팅 · 서핑 · 레일바이크 · 패러글라이딩
            Map.entry("LS02", PlaceType.LEISURE),
            Map.entry("LS03", PlaceType.LEISURE),
            Map.entry("LS04", PlaceType.LEISURE),

            // 아울렛 · 몰 · 백화점 · 전통시장
            Map.entry("SH01", PlaceType.ETC),
            Map.entry("SH02", PlaceType.ETC),
            Map.entry("SH06", PlaceType.ETC),
            // 반려용품점 · 편의점 · 마트
            Map.entry("SH05", PlaceType.ETC),
            Map.entry("SH07", PlaceType.ETC)
    );

    /**
     * 대분류 폴백입니다.
     *
     * 위 표에 없는 중분류가 오면 이것으로 떨어집니다.
     * 소스가 분류를 늘려도 조용히 틀리지 않고 대략 맞는 자리로 갑니다.
     */
    private static final Map<String, PlaceType> BY_MAJOR = Map.of(
            "NA", PlaceType.PARK,
            "VE", PlaceType.PARK,
            "AC", PlaceType.STAY,
            "HS", PlaceType.CULTURE,
            "EX", PlaceType.LEISURE,
            "FD", PlaceType.RESTAURANT,
            "LS", PlaceType.LEISURE,
            "SH", PlaceType.ETC
    );

    /**
     * 소분류까지 봐야 하는 유일한 자리입니다.
     *
     * LS01 은 레저 스포츠인데 그 안의 LS011900 서른두 건이 전부 걷기길입니다.
     * 둘레길과 황톳길, 해안산책로라 레저가 아니라 공원 쪽이 맞습니다.
     * 나머지 LS01 세 건은 스노우파크와 놀이시설이라 레저 그대로입니다.
     */
    private static final String WALKING_TRAIL_MINOR = "LS011900";

    /**
     * 문화정보원의 카테고리3 입니다.
     */
    private static final Map<String, PlaceType> BY_CULTURE_CATEGORY = Map.of(
            "반려동물용품", PlaceType.ETC,
            "동물병원", PlaceType.VET,
            "여행지", PlaceType.PARK,
            "박물관", PlaceType.CULTURE,
            "미술관", PlaceType.CULTURE,
            "문예회관", PlaceType.CULTURE,
            "카페", PlaceType.CAFE,
            "펜션", PlaceType.STAY,
            "호텔", PlaceType.STAY,
            "식당", PlaceType.RESTAURANT
    );

    /**
     * 동선 중에 들르는 보급 지점으로 볼 분류입니다.
     *
     * 명세가 정해 둔 것입니다.
     * 지도 범례가 동물병원을 placeType 이 VET 인 장소로,
     * 반려견 간식과 용품점을 supplyPoint 가 true 인 장소로 가릅니다.
     *
     * SH05 와 SH07 만 넣고 SH02 와 SH06 은 넣지 않습니다.
     * 아울렛과 몰, 전통시장은 그 자체가 목적지이지 보급 지점이 아닙니다.
     *
     * 이 값은 "간식을 판다" 는 뜻이 아니라 "장소 자체가 보급 지점" 이라는 뜻입니다.
     */
    private static final Set<String> SUPPLY_MIDDLE = Set.of("SH05", "SH07");
    private static final String SUPPLY_CULTURE_CATEGORY = "반려동물용품";

    private PlaceTypeMapper() {
    }

    /**
     * 서비스 카테고리를 정합니다.
     *
     * 고캠핑은 분류를 보지 않고 소스만 봅니다.
     * 고캠핑 API 가 야영장 정보 서비스라 정의상 전부 야영장이기 때문입니다.
     * induty 로 판정하면 그 값이 빈 행 여섯 개가 어디로도 못 갑니다.
     * 그 여섯은 이름에 글램핑과 캠핑장이 들어 있는 멀쩡한 야영장입니다.
     *
     * 어디에도 걸리지 않으면 ETC 입니다. 판정을 포기하지 않고 담습니다.
     */
    public static PlaceType resolve(SourceType source, String major, String middle, String minor) {
        if (source == SourceType.GOCAMPING) {
            return PlaceType.CAMPING;
        }
        if (source == SourceType.MOIS_VET) {
            return PlaceType.VET;
        }
        if (source == SourceType.CULTURE_CSV) {
            return BY_CULTURE_CATEGORY.getOrDefault(trim(middle), PlaceType.ETC);
        }

        // 공사 계열입니다
        if (WALKING_TRAIL_MINOR.equals(trim(minor))) {
            return PlaceType.PARK;
        }
        PlaceType byMiddle = BY_MIDDLE.get(trim(middle));
        if (byMiddle != null) {
            return byMiddle;
        }
        PlaceType byMajor = BY_MAJOR.get(trim(major));
        return byMajor != null ? byMajor : PlaceType.ETC;
    }

    /**
     * 동선 중 들르는 보급 지점인지 봅니다.
     */
    public static boolean isSupplyPoint(SourceType source, String middle) {
        if (source == SourceType.CULTURE_CSV) {
            return SUPPLY_CULTURE_CATEGORY.equals(trim(middle));
        }
        if (source == SourceType.PET_TOUR) {
            return SUPPLY_MIDDLE.contains(trim(middle));
        }
        return false;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
