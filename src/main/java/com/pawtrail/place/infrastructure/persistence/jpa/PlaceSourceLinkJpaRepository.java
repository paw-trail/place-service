package com.pawtrail.place.infrastructure.persistence.jpa;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlaceSourceLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface PlaceSourceLinkJpaRepository extends JpaRepository<PlaceSourceLink, UUID> {

    Optional<PlaceSourceLink> findBySourceAndSourceId(SourceType source, String sourceId);

    List<PlaceSourceLink> findAllByPlaceId(UUID placeId);
}
