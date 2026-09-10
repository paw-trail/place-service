package com.pawtrail.place.infrastructure.persistence;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlaceSourceLink;
import com.pawtrail.place.domain.repository.PlaceSourceLinkRepository;
import com.pawtrail.place.infrastructure.persistence.jpa.PlaceSourceLinkJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PlaceSourceLinkRepositoryImpl implements PlaceSourceLinkRepository {

    private final PlaceSourceLinkJpaRepository placeSourceLinkJpaRepository;

    @Override
    public PlaceSourceLink save(PlaceSourceLink link) {
        return placeSourceLinkJpaRepository.save(link);
    }

    @Override
    public Optional<PlaceSourceLink> findById(UUID id) {
        return placeSourceLinkJpaRepository.findById(id);
    }

    @Override
    public void delete(PlaceSourceLink link) {
        placeSourceLinkJpaRepository.delete(link);
    }

    @Override
    public Optional<PlaceSourceLink> findBySourceAndSourceId(SourceType source, String sourceId) {
        return placeSourceLinkJpaRepository.findBySourceAndSourceId(source, sourceId);
    }

    @Override
    public List<PlaceSourceLink> findAllByPlaceId(UUID placeId) {
        return placeSourceLinkJpaRepository.findAllByPlaceId(placeId);
    }
}
