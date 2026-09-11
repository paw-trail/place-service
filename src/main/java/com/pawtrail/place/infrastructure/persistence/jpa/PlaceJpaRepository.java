package com.pawtrail.place.infrastructure.persistence.jpa;

import com.pawtrail.place.domain.model.Place;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface PlaceJpaRepository extends JpaRepository<Place, UUID> {

    List<Place> findByAddressNormalized(String addressNormalized);

    /**
     * 좌표가 가까운 장소를 찾습니다.
     *
     * 네이티브 질의인 이유는 ST_DWithin 을 JPQL 로 쓸 수 없기 때문입니다.
     * 이 프로젝트에 선례가 있습니다.
     * common 의 findByIdForUpdateSkipLocked 와 user 의 findByIdIncludingDeleted 가 같은 형태입니다.
     *
     * 인자 순서가 경도 다음 위도입니다.
     * ST_MakePoint 가 x 를 먼저 받고 x 가 경도이기 때문입니다.
     * 뒤집어 넣으면 좌표가 통째로 다른 곳을 가리키는데 오류가 나지 않습니다.
     *
     * geography 로 형변환하는 이유는 ST_DWithin 이 그 타입일 때 미터로 계산해 주기 때문입니다.
     * geometry 로 두면 거리가 도 단위가 되어 100 을 넘기면 지구 전체가 걸립니다.
     *
     * 거리를 함께 돌려주지 않습니다.
     * 엔티티와 스칼라를 같이 받으려면 프로젝션을 두어야 하는데,
     * 그러면 엔티티를 다시 조회하거나 필드를 하나씩 옮겨야 해서 얻는 것보다 잃는 것이 큽니다.
     * 거리는 부르는 쪽이 좌표로 다시 계산합니다. 후보가 적어 비용이 없습니다.
     *
     * 정렬을 두지 않습니다.
     * 부르는 쪽이 후보를 전부 받아 이름 일치와 거리로 거르므로 순서에 뜻이 없습니다.
     */
    @Query(value = """
            select * from place p
            where ST_DWithin(
                      p.geom,
                      ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                      :meters)
            """, nativeQuery = true)
    List<Place> findNearby(@Param("lat") BigDecimal lat,
                           @Param("lon") BigDecimal lon,
                           @Param("meters") int meters);
}
