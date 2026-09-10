package com.pawtrail.place.infrastructure.persistence.jpa;

import com.pawtrail.place.domain.model.PlaceFacility;
import com.pawtrail.place.domain.model.PlaceFacilityId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 *
 * 기본 키 타입이 PlaceFacilityId 입니다.
 * 복합 키라 UUID 가 아닙니다.
 */
public interface PlaceFacilityJpaRepository
        extends JpaRepository<PlaceFacility, PlaceFacilityId> {

    List<PlaceFacility> findAllByPlaceId(UUID placeId);

    /**
     * 그 장소의 편의시설을 한 번에 지웁니다.
     *
     * 파생 쿼리를 쓰지 않는 이유는 그 방식이 엔티티를 전부 읽어 온 뒤 하나씩 지우기 때문입니다.
     * 적재는 장소 수만큼 도는 배치라 건마다 왕복이 늘면 그대로 시간이 됩니다.
     * 반환값인 지운 행 수도 필요합니다. 적재 로그에 몇 건이 바뀌었는지 남깁니다.
     *
     * flushAutomatically 를 켭니다.
     * 이 쿼리가 건드리는 표는 place_facility 인데
     * 부르기 직전에 고친 것은 place 일 수 있습니다.
     * 하이버네이트는 쿼리가 건드리는 표를 보고 반영 여부를 정하므로
     * place 의 변경이 아직 반영되지 않은 채로 이 쿼리가 나갈 수 있습니다.
     *
     * clearAutomatically 는 켜지 않습니다.
     * 켜면 영속성 컨텍스트가 통째로 비워져 방금 고친 place 가 준영속이 되고
     * 그 변경이 오류 없이 사라집니다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PlaceFacility f where f.placeId = :placeId")
    int deleteAllByPlaceId(@Param("placeId") UUID placeId);
}
