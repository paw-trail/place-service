package com.pawtrail.place.infrastructure.persistence;

import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.infrastructure.persistence.jpa.PlaceJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PlaceRepositoryImpl implements PlaceRepository {

    private final PlaceJpaRepository placeJpaRepository;

    @Override
    public Place save(Place place) {
        return placeJpaRepository.save(place);
    }

    @Override
    public Optional<Place> findById(UUID id) {
        return placeJpaRepository.findById(id);
    }
}
