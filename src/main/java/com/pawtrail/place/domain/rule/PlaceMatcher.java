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

    // 후보를 찾을 때 쓰는 반경입니다
    //
    // 두 임계값 중 넓은 쪽으로 고정합니다.
    // 새 레코드의 좌표 출처만 보고 반경을 정하면 판정이 적재 순서에 따라 갈립니다.
    // 새 레코드가 원본이고 기존 장소가 지오코딩인 쌍은 300m 까지 봐야 하는데
    // 100m 로 조회하면 후보로 올라오지도 않습니다.
    // 반대 순서로 들어오면 잡히므로 같은 쌍이 어느 쪽이 먼저냐로 달라집니다.
    //
    // 넓게 받아도 거짓 병합이 늘지 않습니다.
    // 아래 판정이 두 장소의 좌표 출처를 보고 실제 임계값을 다시 적용합니다.
    public static final int SEARCH_METERS = GEOCODED_METERS;

    // 지구 반지름입니다. 하버사인 거리 계산에 씁니다
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

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
     * 후보는 SEARCH_METERS 로 넓게 받아 온 것이며 여기서 실제 임계값을 적용합니다.
     * 두 장소의 좌표 출처가 임계값을 정하므로 후보를 받아본 뒤에야 판정할 수 있습니다.
     *   둘 다 원본이면 100m
     *   한쪽만 지오코딩이면 300m
     *   둘 다 지오코딩이면 병합하지 않음
     *
     * 지오코딩 좌표끼리 병합하지 않는 이유는 둘 다 주소에서 만든 값이라
     * 오차가 겹치면 서로 다른 장소가 같은 좌표로 보이기 때문입니다.
     *
     * 이름 일치가 여기서도 전제입니다. 300m 안에는 서로 다른 장소가 얼마든지 있습니다.
     *
     * 가장 가까운 후보를 고릅니다.
     * 임계값을 통과한 것이 여럿이면 가까운 쪽이 같은 장소일 가능성이 높습니다.
     */
    public static Match matchByCoordinate(Place incoming, List<Place> candidates) {
        if (incoming.getNameNormalized() == null || candidates == null) {
            return Match.none();
        }

        Place best = null;
        double bestDistance = Double.MAX_VALUE;
        MatchMethod bestMethod = null;

        for (Place candidate : candidates) {
            if (!sameName(incoming, candidate)) {
                continue;
            }
            MatchMethod method = methodFor(incoming, candidate);
            if (method == null) {
                continue;
            }
            Double distance = distanceBetween(incoming, candidate);
            if (distance == null) {
                continue;
            }
            int limit = method == MatchMethod.COORD_GEOCODED ? GEOCODED_METERS : ORIGINAL_METERS;
            if (distance > limit) {
                continue;
            }
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
                bestMethod = method;
            }
        }

        return best == null
                ? Match.none()
                : Match.of(best, bestMethod, confidenceOf(bestMethod, bestDistance));
    }

    /**
     * 두 장소 사이의 거리를 미터로 잽니다.
     *
     * 저장소가 ST_DWithin 으로 걸러 주지만 그 결과에는 거리가 없습니다.
     * 엔티티와 거리를 함께 받으려면 프로젝션을 두어야 하는데
     * 그러면 엔티티를 다시 조회하거나 필드를 하나씩 옮겨야 합니다.
     * 후보가 적어 여기서 다시 재는 편이 쌉니다.
     *
     * 하버사인 공식입니다. 지구를 구로 보고 두 점 사이의 대원 거리를 구합니다.
     * ST_Distance 는 타원체로 계산해 수 미터 차이가 날 수 있으나
     * 임계값이 100m 와 300m 라 판정이 갈릴 만한 차이가 아닙니다.
     */
    public static Double distanceBetween(Place a, Place b) {
        if (a.getLat() == null || a.getLon() == null || b.getLat() == null || b.getLon() == null) {
            return null;
        }
        double lat1 = Math.toRadians(a.getLat().doubleValue());
        double lon1 = Math.toRadians(a.getLon().doubleValue());
        double lat2 = Math.toRadians(b.getLat().doubleValue());
        double lon2 = Math.toRadians(b.getLon().doubleValue());

        double sinLat = Math.sin((lat2 - lat1) / 2);
        double sinLon = Math.sin((lon2 - lon1) / 2);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }

    private static boolean sameName(Place a, Place b) {
        return a.getNameNormalized() != null
                && a.getNameNormalized().equals(b.getNameNormalized());
    }

    /**
     * 두 장소의 좌표 출처로 판정 방법을 정합니다.
     *
     * 병합하지 않을 조합이면 null 입니다.
     *
     * 지오코딩끼리는 병합하지 않습니다.
     * 둘 다 주소에서 만든 값이라 오차가 겹치면
     * 서로 다른 장소가 같은 좌표로 보입니다.
     *
     * 변환 좌표가 낀 조합도 지금은 병합하지 않습니다.
     * EPSG:5174 에서 4326 으로 옮길 때 변환식이 하나가 아니라
     * 어느 파라미터를 쓰느냐로 결과가 수 미터에서 수십 미터까지 달라집니다.
     * 그 오차를 재보기 전에는 임계값을 정할 수 없고,
     * 원본과 같은 100m 로 뭉뚱그리면 명세가 경고한 그대로가 됩니다.
     *
     * 행정안전부 동물병원 CSV 가 유일한 변환 좌표 소스인데 아직 들어오지 않아
     * 지금 적재본에는 이 조합이 한 건도 없습니다.
     * ingest 착수 때 실제 오차를 재고 임계값을 정하면서 함께 엽니다.
     *
     * 한쪽이 지오코딩이고 다른 쪽이 변환 좌표인 경우는 지오코딩 규칙을 따릅니다.
     * 그쪽 오차가 이미 더 크므로 300m 안이면 받아들일 만합니다.
     *
     * 주소 일치로는 여전히 병합됩니다.
     * 이 판정은 좌표 단계에만 해당하고 ADDRESS 가 1 순위입니다.
     */
    private static MatchMethod methodFor(Place a, Place b) {
        CoordSource left = a.getCoordSource();
        CoordSource right = b.getCoordSource();
        if (left == null || right == null) {
            return null;
        }
        if (left == CoordSource.GEOCODED && right == CoordSource.GEOCODED) {
            return null;
        }
        if (left == CoordSource.GEOCODED || right == CoordSource.GEOCODED) {
            return MatchMethod.COORD_GEOCODED;
        }
        if (left == CoordSource.CONVERTED || right == CoordSource.CONVERTED) {
            return null;
        }
        return MatchMethod.COORD_ORIGINAL;
    }

    /**
     * 매칭 신뢰도입니다.
     *
     * 검증할 때 낮은 것부터 뽑아 눈으로 확인하기 위한 값입니다.
     * 원본 좌표끼리는 거리로 나눕니다. 가까울수록 확실합니다.
     * 지오코딩이 낀 것은 일괄로 낮게 둡니다. 거리 자체를 덜 믿기 때문입니다.
     *
     * 주소 일치는 값을 두지 않습니다. 견줄 대상이 없어 항상 같은 숫자가 됩니다.
     */
    private static BigDecimal confidenceOf(MatchMethod method, double distance) {
        if (method == MatchMethod.COORD_GEOCODED) {
            return new BigDecimal("0.60");
        }
        return distance <= 50 ? new BigDecimal("0.90") : new BigDecimal("0.80");
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
