package com.pawtrail.place.infrastructure.persistence.jpa;

import com.pawtrail.place.domain.enums.PendingStatus;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface PlacePendingUpdateJpaRepository
        extends JpaRepository<PlacePendingUpdate, UUID> {

    /**
     * 처리하지 않은 것을 발견 시각 최신순으로 돌려줍니다.
     *
     * 이 조회는 (place_id, field_name, status) 인덱스를 타지 않습니다.
     * status 로 거르고 detected_at 으로 정렬하므로 (status, detected_at) 이 맞습니다.
     *
     * 그 인덱스를 두지 않은 것은 이 표에 행이 거의 없기 때문입니다.
     * 대기 행은 관리자가 손댄 장소에서만 생기며 지금은 한 건도 없습니다.
     * 효과를 잴 부하가 없으면 넣지 않는다는 기준을 따랐고, 필요해지면 새 번호로 붙입니다.
     */
    Page<PlacePendingUpdate> findByStatusOrderByDetectedAtDesc(PendingStatus status,
                                                               Pageable pageable);

    /**
     * 그 장소의 그 필드에서 아직 처리하지 않은 값을 찾습니다.
     *
     * 같은 값이면 대기 행을 새로 만들지 않으므로 처리 전 행은 필드당 하나입니다.
     * 다른 소스가 서로 다른 값을 보내면 둘이 될 수 있어 가장 최근 것을 씁니다.
     */
    Optional<PlacePendingUpdate> findFirstByPlaceIdAndFieldNameAndStatusOrderByDetectedAtDesc(
            UUID placeId, String fieldName, PendingStatus status);

    /**
     * 같은 값이 아직 처리되지 않았거나 반려된 적이 있는지 봅니다.
     *
     * 승인된 것을 제외하는 이유는 그 값이 이미 place 에 반영되어
     * 수집이 달라졌다고 보지 않기 때문입니다.
     *
     * newValue 가 null 인 경우는 오지 않습니다.
     * 수집은 새 값이 비면 달라진 것으로 보지 않아 대기 행 자체를 만들지 않습니다.
     */
    boolean existsByPlaceIdAndFieldNameAndNewValueAndStatusIn(
            UUID placeId, String fieldName, String newValue, Collection<PendingStatus> statuses);
}
