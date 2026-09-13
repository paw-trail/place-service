package com.pawtrail.place.infrastructure.persistence.jpa;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlaceSourceDetach;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface PlaceSourceDetachJpaRepository
        extends JpaRepository<PlaceSourceDetach, UUID> {

    /**
     * 이 소스 레코드가 떼어진 장소의 식별자를 찾습니다.
     *
     * 엔티티가 아니라 식별자만 뽑습니다.
     * 적재 건마다 도는 조회라 쓰지 않을 컬럼까지 읽어 올 이유가 없습니다.
     *
     * idx_place_source_detach_source 를 탑니다.
     */
    @Query("""
            select d.placeId from PlaceSourceDetach d
            where d.source = :source and d.sourceId = :sourceId
            """)
    List<UUID> findDetachedPlaceIds(@Param("source") SourceType source,
                                    @Param("sourceId") String sourceId);
}
