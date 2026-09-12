package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.PlacePendingUpdate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 반영 대기 값을 다루는 약속입니다.
 *
 * 지우는 메서드가 없습니다.
 * 승인과 반려는 status 를 바꾸는 것이고 행은 남습니다.
 * 반려한 값을 다음 수집에서 다시 쌓지 않으려면 그 기록이 남아 있어야 합니다.
 */
public interface PlacePendingUpdateRepository {

    PlacePendingUpdate save(PlacePendingUpdate pendingUpdate);

    /**
     * 저장하고 그 자리에서 데이터베이스로 내보냅니다.
     *
     * save 만 부르면 실제 INSERT 가 트랜잭션 커밋 시점으로 밀립니다.
     * 대기 행 삽입은 실패해도 그 건만 건너뛰기로 했는데,
     * 예외가 커밋에서 터지면 부르는 쪽의 try 블록을 이미 빠져나온 뒤라 잡을 수 없습니다.
     * 그러면 REQUIRES_NEW 로 뺀 뜻이 없어지고 바깥 트랜잭션까지 함께 죽습니다.
     */
    PlacePendingUpdate saveAndFlush(PlacePendingUpdate pendingUpdate);

    Optional<PlacePendingUpdate> findById(UUID id);

    /**
     * 그 대기 값을 잠그고 찾아옵니다.
     *
     * 승인과 반려가 씁니다.
     * 아직 처리하지 않았는지 보고 처리하는 사이를 다른 요청이 끼어들지 못하게 합니다.
     *
     * 잠그지 않으면 두 요청이 모두 처리 전이라고 보고 각자 진행합니다.
     * 하나가 승인하고 하나가 반려하면 place 에는 값이 반영됐는데 행은 반려로 남습니다.
     * 반려한 값은 다음 수집에서 다시 올라오지 않으므로 그 상태가 그대로 굳습니다.
     */
    Optional<PlacePendingUpdate> findByIdForUpdate(UUID id);

    /**
     * 관리자가 처리할 것만 최신순으로 돌려줍니다.
     *
     * GET /api/v1/admin/places/pending 이 씁니다.
     *
     * 처리한 것은 담지 않습니다.
     * 승인하거나 반려한 행까지 보이면 목록이 계속 길어지고
     * 배지에 뜨는 숫자가 "할 일 개수" 라는 뜻을 잃습니다.
     * 관리자 아웃박스 목록을 재시도 상한을 넘긴 건만 보여주기로 한 것과 같은 기준입니다.
     */
    Page<PlacePendingUpdate> findPending(Pageable pageable);

    /**
     * 그 장소의 그 필드에 아직 처리하지 않은 값이 있는지 봅니다.
     *
     * 주소를 승인할 때 씁니다.
     * 주소는 도로명과 지번이 한 덩어리라 도로명만 반영하면 지번이 지워지거나
     * 반쪽 주소에서 정규화 값이 나옵니다.
     * 도로명 대기 값을 승인할 때 같은 장소의 지번 대기 값을 찾아 함께 넘깁니다.
     *
     * 지번 대기 값이 없으면 그 소스가 지번을 바꾸지 않았다는 뜻입니다.
     * 수집이 값이 달라졌을 때만 대기 행을 만들기 때문에 없다는 것 자체가 근거가 됩니다.
     */
    Optional<PlacePendingUpdate> findPendingByPlaceIdAndFieldName(UUID placeId, String fieldName);

    /**
     * 같은 값이 이미 대기 중이거나 반려된 적이 있는지 봅니다.
     *
     * 수집이 대기 행을 만들기 전에 씁니다.
     *
     * 소스가 값을 고치지 않는 한 같은 차이가 수집마다 발견됩니다.
     * 그때마다 행을 만들면 목록에 같은 값이 여러 줄 뜨고,
     * 반려한 것도 다시 올라와 관리자가 같은 판단을 되풀이하게 됩니다.
     *
     * 승인된 것은 보지 않습니다.
     * 승인되면 place 의 값이 새 값이 되어 수집이 달라졌다고 보지 않습니다.
     */
    boolean existsUnresolved(UUID placeId, String fieldName, String newValue);
}
