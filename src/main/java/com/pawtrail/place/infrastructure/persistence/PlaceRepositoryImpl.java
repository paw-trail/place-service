package com.pawtrail.place.infrastructure.persistence;

import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.infrastructure.persistence.jpa.PlaceJpaRepository;
import java.math.BigDecimal;
import java.util.Collection;
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

    @Override
    public Optional<Place> findByIdForUpdate(UUID id) {
        return placeJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<Place> findAllById(Collection<UUID> ids) {
        return placeJpaRepository.findAllById(ids);
    }

    @Override
    public List<Place> findByAddressNormalized(String addressNormalized) {
        if (addressNormalized == null || addressNormalized.isBlank()) {
            // 주소를 정규화하지 못한 레코드가 적재본에 다섯 건 있습니다
            // 빈 값으로 조회하면 같은 처지의 행이 전부 후보가 되므로 아예 찾지 않습니다
            return List.of();
        }
        return placeJpaRepository.findByAddressNormalized(addressNormalized);
    }

    @Override
    public List<Place> findNearby(BigDecimal lat, BigDecimal lon, int meters) {
        if (lat == null || lon == null) {
            return List.of();
        }
        return placeJpaRepository.findNearby(lat, lon, meters);
    }
}
