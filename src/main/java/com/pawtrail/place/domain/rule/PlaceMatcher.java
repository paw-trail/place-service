package com.pawtrail.place.domain.rule;

import com.pawtrail.place.domain.enums.CoordSource;
import com.pawtrail.place.domain.enums.MatchMethod;
import com.pawtrail.place.domain.model.Place;
import java.math.BigDecimal;
import java.util.List;

/**
 * 새로 들어온 장소가 이미 적재된 장소와 같은 곳인지 판정합니다.
 *
 * 후보를 찾아오는 일은 하지 않습니다. 부르는 쪽이 저장소에서 받아 넘깁니다.
 * 그래야 이 클래스가 순수 함수로 남고 판정 규칙만 검사할 수 있습니다.
 *
 * 거짓 병합이 거짓 분리보다 훨씬 위험합니다.
 * 거짓 병합은 강남점 조건에 홍대점 원문을 붙이는 식으로 틀린 근거를 만드는데,
 * 근거 제시가 정체성인 서비스에 치명적입니다.
 * 그래서 애매하면 병합하지 않습니다.
 */
public final class PlaceMatcher {

    // 원본 좌표끼리의 임계값입니다
    //
    // 50m 가 아니라 100m 인 이유는 소스마다 대표 좌표를 다르게 잡기 때문입니다.
    // 건물 중심을 쓰는 곳과 입구를 쓰는 곳이 있어
    // 주소와 이름이 둘 다 같은데 좌표만 100m 가까이 떨어진 쌍이 적재본에 있습니다.
    public static final int ORIGINAL_METERS = 100;

    // 한쪽이 지오코딩 좌표일 때의 임계값입니다
    //
    // 주소를 좌표로 바꾼 값이라 원본보다 오차가 큽니다.
    public static final int GEOCODED_METERS = 300;

    private PlaceMatcher() {
    }

    /**
     * 주소가 일치하는 후보 중에서 같은 장소를 고릅니다.
     *
     * 이름 일치가 전제입니다.
     * 주소가 같고 이름이 다른 쌍이 적재본에 백 여 건 있습니다.
     * 북촌8경과 북촌문화센터, 스타필드코엑스몰과 다이소코엑스몰점 같은 것들입니다.
     * 한 건물에 여러 장소가 있으면 주소가 같아지므로 이름을 봐야 합니다.
     *
     * 후보가 여럿이면 첫 번째를 고릅니다.
     * 주소와 이름이 둘 다 같은 장소가 여럿이라면 그것들끼리도 이미 병합되어 있어야 하므로
     * 정상 상태에서는 하나뿐입니다.
     */
    public static Match matchByAddress(Place incoming, List<Place> candidates) {
        if (incoming.getNameNormalized() == null || candidates == null) {
            return Match.none();
        }
        for (Place candidate : candidates) {
            if (sameName(incoming, candidate)) {
                return Match.of(candidate, MatchMethod.ADDRESS, null);
            }
        }
        return Match.none();
    }

    /**
     * 좌표가 가까운 후보 중에서 같은 장소를 고릅니다.
     *
     * 이름 일치가 여기서도 전제입니다.
     * 100m 안에는 서로 다른 장소가 얼마든지 있습니다.
     *
     * 지오코딩 좌표끼리는 병합하지 않습니다.
     * 둘 다 주소에서 만든 값이라 오차가 겹치면 서로 다른 장소가 같은 좌표로 보입니다.
     * 한쪽만 지오코딩이면 임계값을 넓혀 300m 로 봅니다.
     *
     * 거리는 부르는 쪽이 이미 좁혀서 넘깁니다.
     * 저장소가 ST_DWithin 으로 걸러 주므로 여기서 다시 재지 않습니다.
     * 다만 어느 임계값으로 걸렀는지는 부르는 쪽이 알아야 하므로
     * 이 클래스가 그 값을 상수로 들고 있습니다.
     */
    public static Match matchByCoordinate(Place incoming, List<Place> candidates) {
        if (incoming.getNameNormalized() == null || candidates == null) {
            return Match.none();
        }
        for (Place candidate : candidates) {
            if (!sameName(incoming, candidate)) {
                continue;
            }
            if (bothGeocoded(incoming, candidate)) {
                continue;
            }
            MatchMethod method = eitherGeocoded(incoming, candidate)
                    ? MatchMethod.COORD_GEOCODED
                    : MatchMethod.COORD_ORIGINAL;
            return Match.of(candidate, method, confidenceOf(method));
        }
        return Match.none();
    }

    /**
     * 이 레코드로 좌표 후보를 찾을 때 쓸 반경입니다.
     *
     * 지오코딩 좌표면 넓게 찾습니다.
     * 상대가 원본 좌표일 수 있고 그때는 300m 까지 보기 때문입니다.
     * 좁게 찾으면 300m 짝을 아예 후보로 못 받습니다.
     */
    public static int searchRadiusOf(Place incoming) {
        return incoming.getCoordSource() == CoordSource.GEOCODED
                ? GEOCODED_METERS
                : ORIGINAL_METERS;
    }

    private static boolean sameName(Place a, Place b) {
        return a.getNameNormalized() != null
                && a.getNameNormalized().equals(b.getNameNormalized());
    }

    private static boolean bothGeocoded(Place a, Place b) {
        return a.getCoordSource() == CoordSource.GEOCODED
                && b.getCoordSource() == CoordSource.GEOCODED;
    }

    private static boolean eitherGeocoded(Place a, Place b) {
        return a.getCoordSource() == CoordSource.GEOCODED
                || b.getCoordSource() == CoordSource.GEOCODED;
    }

    /**
     * 매칭 신뢰도입니다.
     *
     * 검증할 때 낮은 것부터 뽑아 눈으로 확인하기 위한 값입니다.
     * 주소 일치는 값을 두지 않습니다. 견줄 대상이 없어 항상 같은 숫자가 됩니다.
     */
    private static BigDecimal confidenceOf(MatchMethod method) {
        return method == MatchMethod.COORD_GEOCODED
                ? new BigDecimal("0.60")
                : new BigDecimal("0.80");
    }

    /**
     * 판정 결과입니다.
     *
     * matched 가 거짓이면 같은 장소를 못 찾은 것이며 새 장소로 만들어야 합니다.
     */
    public record Match(boolean matched, Place place, MatchMethod method, BigDecimal confidence) {

        static Match of(Place place, MatchMethod method, BigDecimal confidence) {
            return new Match(true, place, method, confidence);
        }

        static Match none() {
            return new Match(false, null, null, null);
        }
    }
}
