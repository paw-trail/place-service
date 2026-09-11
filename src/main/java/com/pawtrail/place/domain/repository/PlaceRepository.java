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
     * 거리를 인자로 받는 이유는 단계마다 임계값이 다르기 때문입니다.
     * 원본 좌표끼리는 100m 이고 한쪽이 지오코딩 좌표면 300m 입니다.
     * 행정안전부 CSV 의 변환 좌표는 임계값이 아직 정해지지 않았습니다.
     * 명세가 실제 변환 오차를 재본 뒤 ingest 착수 때 정하기로 두었습니다.
     *
     * 좌표를 받는 순서가 위도 다음 경도입니다.
     * 구현이 ST_MakePoint 에 넘길 때는 순서를 뒤집어야 합니다. 그쪽은 경도가 먼저입니다.
     */
    List<Place> findNearby(BigDecimal lat, BigDecimal lon, int meters);
}
