package com.pawtrail.place.infrastructure.persistence.jpa;

import com.pawtrail.place.domain.model.PlaceFacility;
import com.pawtrail.place.domain.model.PlaceFacilityId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    List<PlaceFacility> findAllByPlaceIdIn(Collection<UUID> placeIds);

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
     *
     * @Transactional 을 붙이는 이유
     * 수정 쿼리는 트랜잭션 안에서만 실행할 수 있어
     * 트랜잭션 없이 부르면 TransactionRequiredException 이 납니다.
     * 지금은 부르는 서비스가 없지만 생겼을 때 빠뜨리면 그 자리에서 터집니다.
     *
     * jakarta 가 아니라 스프링 것이어야 합니다.
     * jakarta.transaction.Transactional 도 컴파일은 되지만
     * propagation 이나 readOnly 같은 스프링 속성을 쓸 수 없습니다.
     *
     * 이것은 "혼자 불려도 안 터진다" 는 최소 보장일 뿐입니다.
     * 편의시설을 지우고 다시 넣는 교체 흐름을 만들 때는
     * 삭제와 save 를 서비스에서 한 트랜잭션으로 묶어야 합니다.
     * 그러지 않으면 지우기만 하고 넣기가 실패했을 때 편의시설이 통째로 비어 버립니다.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from PlaceFacility f where f.placeId = :placeId")
    int deleteAllByPlaceId(@Param("placeId") UUID placeId);
}
