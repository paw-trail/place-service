package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.Place;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 장소를 저장하고 찾아오는 약속입니다.
 *
 * 이 인터페이스에는 JPA 라는 단어가 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 하는지는 infrastructure 가 정합니다.
 *
 * 하드 딜리트를 쓰지 않습니다.
 * 폐업은 status 를 CLOSED 로 바꿔 표현하며 행을 지우지 않습니다.
 * 지우면 즐겨찾기 · 방문 기록 · 후기의 참조가 끊깁니다.
 * 그래서 delete 를 두지 않았습니다.
 */
public interface PlaceRepository {

    Place save(Place place);

    Optional<Place> findById(UUID id);

    /**
     * 병합 후보를 주소로 찾습니다. 판정 ADDRESS 단계가 씁니다.
     *
     * 적재본에서 병합 쌍 148 개 중 113 개가 이 단계에서 붙었습니다.
     * 76% 라 여기서 끝나면 좌표 조회를 부르지 않아도 됩니다.
     *
     * 이름으로 찾는 메서드를 두지 않은 것이 중요합니다.
     * 이름만 같고 붙지 않는 쌍이 적재본에 4,946 개 있어
     * 흔한 이름에서 후보가 수십 개씩 쏟아집니다.
     * 이름 일치는 후보를 거르는 조건이지 후보를 찾는 열쇠가 아닙니다.
     */
    List<Place> findByAddressNormalized(String addressNormalized);

    /**
     * 병합 후보를 좌표로 찾습니다. 판정 COORD 단계가 씁니다.
     *
     * 반경은 PlaceMatcher.SEARCH_METERS 를 넘깁니다.
     * 항상 가장 넓은 임계값으로 찾고 실제 판정은 PlaceMatcher 가 합니다.
     *
     * 새 레코드의 좌표 출처만 보고 반경을 정하면 판정이 적재 순서에 따라 갈립니다.
     * 새 레코드가 원본이고 기존 장소가 지오코딩인 쌍은 300m 까지 봐야 하는데
     * 좁게 조회하면 후보로 올라오지도 않습니다.
     * 반대 순서로 들어오면 잡히므로 같은 쌍이 어느 쪽이 먼저냐로 달라집니다.
     *
     * 좌표를 받는 순서가 위도 다음 경도입니다.
     * 구현이 ST_MakePoint 에 넘길 때는 순서를 뒤집어야 합니다. 그쪽은 경도가 먼저입니다.
     */
    List<Place> findNearby(BigDecimal lat, BigDecimal lon, int meters);
}
