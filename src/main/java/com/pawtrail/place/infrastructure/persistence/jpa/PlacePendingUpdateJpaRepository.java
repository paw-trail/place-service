package com.pawtrail.place.infrastructure.persistence.jpa;

import com.pawtrail.place.domain.enums.PendingStatus;
import jakarta.persistence.LockModeType;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     *
     * 식별자를 두 번째 정렬 기준으로 둡니다.
     * 발견 시각이 같은 행의 순서가 정해져 있지 않으면
     * 페이지를 넘길 때 같은 행이 두 번 나오거나 한 행이 통째로 빠집니다.
     * 한 수집이 같은 장소에서 여러 필드를 발견하면 시각이 밀리초까지 같습니다.
     * 식별자가 UUID 버전 7 이라 그 값의 순서가 곧 만들어진 순서입니다.
     */
    Page<PlacePendingUpdate> findByStatusOrderByDetectedAtDescIdDesc(PendingStatus status,
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
     * 그 대기 행에 쓰기 잠금을 걸고 읽습니다.
     *
     * 다른 트랜잭션이 같은 행을 잠그려 하면 이쪽이 끝날 때까지 기다립니다.
     * 승인과 반려에서 상태 검사와 처리 사이를 막는 데 씁니다.
     *
     * 장소를 잠그는 것과 자리가 다릅니다.
     * 그쪽은 그 장소의 연결이나 주소처럼 여러 행에 걸친 판단을 막는 것이고,
     * 여기는 이 행 하나가 두 번 처리되는 것을 막습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlacePendingUpdate p where p.id = :id")
    Optional<PlacePendingUpdate> findByIdForUpdate(@Param("id") UUID id);

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
