package com.pawtrail.place.domain.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 좌표를 저장할 수 있는 형태로 다듬습니다.
 *
 * 하는 일이 둘입니다. 범위 검사와 자릿수 반올림입니다.
 * 지오코딩은 여기서 하지 않습니다. 외부 호출이라 순수 함수가 아니게 됩니다.
 * 이 클래스는 "이 좌표를 믿을 수 있는가" 까지만 답하고 적재 단계가 그 답을 받아 처리합니다.
 */
public final class CoordinateNormalizer {

    // 대한민국의 대략적인 경계입니다
    //
    // 정확한 국경선이 아니라 "명백히 밖인 값" 을 거르는 용도입니다.
    // 마라도와 독도, 백령도까지 넉넉히 들어옵니다.
    private static final BigDecimal MIN_LAT = new BigDecimal("33.0");
    private static final BigDecimal MAX_LAT = new BigDecimal("38.7");
    private static final BigDecimal MIN_LON = new BigDecimal("124.5");
    private static final BigDecimal MAX_LON = new BigDecimal("132.0");

    // place.lat 과 place.lon 이 numeric(10,7) 입니다
    //
    // 소수 일곱 자리면 약 1cm 정밀도라 이 서비스에는 넘치게 정확합니다.
    // 병합의 좌표 근접 임계값이 100m 입니다.
    private static final int SCALE = 7;

    private CoordinateNormalizer() {
    }

    /**
     * 좌표가 쓸 만한지 판정하고 다듬은 값을 돌려줍니다.
     *
     * 못 쓰는 좌표면 값이 비어 있는 결과를 돌려줍니다.
     * 그 경우 적재 단계가 주소로 지오코딩을 시도합니다.
     */
    public static Result normalize(String rawLat, String rawLon) {
        BigDecimal lat = parse(rawLat);
        BigDecimal lon = parse(rawLon);
        if (lat == null || lon == null) {
            return Result.unusable();
        }
        if (!inKorea(lat, lon)) {
            return Result.unusable();
        }
        return Result.of(round(lat), round(lon));
    }

    /**
     * 자릿수를 컬럼에 맞춰 반올림합니다.
     *
     * 버리지 않고 반올림하는 것이 중요합니다.
     * PostgreSQL 이 numeric 에 넣을 때 하는 것이 반올림이므로 같은 방식이어야
     * 나중에 다시 적재해도 값이 흔들리지 않습니다.
     *
     * 미리 자르는 이유는 정밀도가 아니라 geom 정합입니다.
     * 엔티티가 반올림 전 값으로 geom 을 만들면 저장된 좌표와 다른 값에서 나온 것이 됩니다.
     * 적재본에서 소수 일곱 자리를 넘는 행이 위도 기준 14,538 건으로 전체의 83% 였습니다.
     */
    private static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 대한민국 안인지 봅니다.
     *
     * 이 검사가 필요한 이유는 소스가 오류 좌표를 주는 행이 있기 때문입니다.
     * 오산반려동물테마파크와 기흥레스피아호수공원이 똑같이
     * 위도 19.69442748, 경도 117.9925662504 로 옵니다. 필리핀 앞바다입니다.
     *
     * 그대로 담으면 위험합니다.
     * 둘이 정확히 같은 값이라 좌표 근접 판정에서 거리가 0m 입니다.
     * 지금은 이름이 달라 붙지 않지만 같은 오류값을 가진 행이 더 들어오면
     * 서로 다른 장소들이 한 덩어리로 뭉칩니다.
     * 소스의 기본값이나 오류값은 여러 행이 공유하는 것이 흔합니다.
     *
     * 두 장소 모두 실재하고 주소는 멀쩡하므로 버리지 않고 지오코딩으로 넘깁니다.
     */
    private static boolean inKorea(BigDecimal lat, BigDecimal lon) {
        return lat.compareTo(MIN_LAT) >= 0 && lat.compareTo(MAX_LAT) <= 0
                && lon.compareTo(MIN_LON) >= 0 && lon.compareTo(MAX_LON) <= 0;
    }

    private static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            // 숫자가 아닌 값은 없는 것과 같이 다룹니다
            // 부르는 쪽이 하는 일이 "지오코딩으로 넘긴다" 로 같기 때문입니다
            return null;
        }
    }

    /**
     * 정규화 결과입니다.
     *
     * usable 이 거짓이면 lat 과 lon 이 null 이며 지오코딩이 필요합니다.
     */
    public record Result(boolean usable, BigDecimal lat, BigDecimal lon) {

        static Result of(BigDecimal lat, BigDecimal lon) {
            return new Result(true, lat, lon);
        }

        static Result unusable() {
            return new Result(false, null, null);
        }
    }
}
