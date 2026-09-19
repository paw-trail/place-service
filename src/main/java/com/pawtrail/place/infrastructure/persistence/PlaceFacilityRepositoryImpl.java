package com.pawtrail.place.infrastructure.persistence;

import com.pawtrail.place.domain.model.PlaceFacility;
import com.pawtrail.place.domain.repository.PlaceFacilityRepository;
import com.pawtrail.place.infrastructure.persistence.jpa.PlaceFacilityJpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PlaceFacilityRepositoryImpl implements PlaceFacilityRepository {

    private final PlaceFacilityJpaRepository placeFacilityJpaRepository;

    @Override
    public PlaceFacility save(PlaceFacility facility) {
        return placeFacilityJpaRepository.save(facility);
    }

    @Override
    public List<PlaceFacility> findAllByPlaceId(UUID placeId) {
        return placeFacilityJpaRepository.findAllByPlaceId(placeId);
    }

    @Override
    public List<PlaceFacility> findAllByPlaceIdIn(Collection<UUID> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return List.of();
        }
        return placeFacilityJpaRepository.findAllByPlaceIdIn(placeIds);
    }

    @Override
    public int deleteAllByPlaceId(UUID placeId) {
        return placeFacilityJpaRepository.deleteAllByPlaceId(placeId);
    }
}
