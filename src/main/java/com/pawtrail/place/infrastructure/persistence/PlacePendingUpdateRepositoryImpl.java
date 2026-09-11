package com.pawtrail.place.infrastructure.persistence;

import com.pawtrail.place.domain.model.PlacePendingUpdate;
import com.pawtrail.place.domain.repository.PlacePendingUpdateRepository;
import com.pawtrail.place.infrastructure.persistence.jpa.PlacePendingUpdateJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PlacePendingUpdateRepositoryImpl implements PlacePendingUpdateRepository {

    private final PlacePendingUpdateJpaRepository placePendingUpdateJpaRepository;

    @Override
    public PlacePendingUpdate save(PlacePendingUpdate pendingUpdate) {
        return placePendingUpdateJpaRepository.save(pendingUpdate);
    }

    @Override
    public PlacePendingUpdate saveAndFlush(PlacePendingUpdate pendingUpdate) {
        return placePendingUpdateJpaRepository.saveAndFlush(pendingUpdate);
    }

    @Override
    public Optional<PlacePendingUpdate> findById(UUID id) {
        return placePendingUpdateJpaRepository.findById(id);
    }
}
