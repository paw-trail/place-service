package com.pawtrail.place.infrastructure.persistence;

import com.pawtrail.place.domain.enums.PendingStatus;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Override
    public Page<PlacePendingUpdate> findPending(Pageable pageable) {
        return placePendingUpdateJpaRepository
                .findByStatusOrderByDetectedAtDesc(PendingStatus.PENDING, pageable);
    }

    @Override
    public Optional<PlacePendingUpdate> findPendingByPlaceIdAndFieldName(UUID placeId,
                                                                         String fieldName) {
        return placePendingUpdateJpaRepository
                .findFirstByPlaceIdAndFieldNameAndStatusOrderByDetectedAtDesc(
                        placeId, fieldName, PendingStatus.PENDING);
    }

    @Override
    public boolean existsUnresolved(UUID placeId, String fieldName, String newValue) {
        // 승인된 것은 보지 않음
        // 그 값은 이미 place 에 반영돼 수집이 달라졌다고 보지 않음
        return placePendingUpdateJpaRepository
                .existsByPlaceIdAndFieldNameAndNewValueAndStatusIn(
                        placeId, fieldName, newValue,
                        List.of(PendingStatus.PENDING, PendingStatus.REJECTED));
    }
}
