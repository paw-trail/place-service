package com.pawtrail.place.infrastructure.persistence;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlaceSourceDetach;
import com.pawtrail.place.domain.repository.PlaceSourceDetachRepository;
import com.pawtrail.place.infrastructure.persistence.jpa.PlaceSourceDetachJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PlaceSourceDetachRepositoryImpl implements PlaceSourceDetachRepository {

    private final PlaceSourceDetachJpaRepository placeSourceDetachJpaRepository;

    @Override
    public PlaceSourceDetach save(PlaceSourceDetach detach) {
        return placeSourceDetachJpaRepository.save(detach);
    }

    @Override
    public List<UUID> findDetachedPlaceIds(SourceType source, String sourceId) {
        return placeSourceDetachJpaRepository.findDetachedPlaceIds(source, sourceId);
    }
}
