package com.pawtrail.place.application.service;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import com.pawtrail.place.domain.repository.PlacePendingUpdateRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잠긴 장소에 대해 수집이 가져온 값을 대기 행으로 쌓습니다.
 *
 * 별도 서비스인 이유가 트랜잭션 경계 때문입니다.
 *
 * 적재는 청크 하나를 한 트랜잭션으로 묶습니다.
 * 그런데 대기 행 삽입이 실패했을 때 그 건만 건너뛰기로 정했습니다.
 * 같은 트랜잭션 안에서 실패하면 그 예외가 트랜잭션에 rollback-only 를 남겨,
 * 예외를 잡아도 표시가 안 지워지고 커밋이 거부됩니다.
 * 청크 500 건 중 대기 행 하나 때문에 정상 499 건이 통째로 날아갑니다.
 *
 * REQUIRES_NEW 로 빼면 바깥 트랜잭션이 그 실패에 물들지 않습니다.
 * auth 의 TokenRevokeService 가 같은 이유로 별도 빈이었습니다.
 *
 * 자기 호출로는 프록시를 타지 않으므로 반드시 별도 빈이어야 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlacePendingUpdateService {

    private final PlacePendingUpdateRepository pendingUpdateRepository;

    /**
     * 대기 행을 하나 만듭니다.
     *
     * 실패해도 영구 손실이 아닙니다.
     * 수집 배치가 다음에 돌면 같은 차이를 또 발견해 다시 쌓습니다.
     * place 행과 달리 되돌릴 수 없는 값이 아닙니다.
     *
     * 값을 잘라 담지 않습니다.
     * 잘린 주소를 관리자가 승인하면 place 에 깨진 값이 들어갑니다.
     * 전화번호에서 "잘린 번호는 담지 않는다" 로 정한 것과 같은 기준입니다.
     *
     * 예외를 여기서 잡지 않습니다.
     *
     * 잡으면 오히려 부르는 쪽까지 함께 죽습니다.
     * 하이버네이트는 내보내기 중에 제약을 어기면 그 세션을 되돌릴 수밖에 없는 상태로 표시합니다.
     * 여기서 잡고 정상으로 돌아가도 이 메서드를 빠져나갈 때 스프링이 커밋을 시도하다
     * UnexpectedRollbackException 을 던지고, 그 예외는 부르는 쪽에서 잡히지 않습니다.
     * 별도 트랜잭션으로 뺀 뜻이 사라집니다.
     *
     * 예외를 그대로 내보내면 스프링이 이 트랜잭션만 되돌리고 원래 예외를 다시 던집니다.
     * 바깥은 별개의 트랜잭션이라 물들지 않으므로 부르는 쪽이 안전하게 잡을 수 있습니다.
     *
     * 어떤 실패인지 가르는 일도 부르는 쪽이 합니다.
     * 같은 값이 이미 있는 것은 만들 필요가 없었다는 뜻이고,
     * 컬럼 폭을 넘긴 것은 관리자가 알아야 할 실패라 세는 자리가 다릅니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID placeId, String fieldName,
                       String currentValue, String newValue, SourceType source) {
        // saveAndFlush 인 이유는 예외가 나는 시점 때문임
        //
        // save 만 부르면 실제 INSERT 가 커밋 시점으로 밀림
        // 그러면 이 트랜잭션을 되돌리는 과정에서 예외가 나 부르는 쪽이 종류를 가릴 수 없음
        // 여기서 내보내야 어떤 제약을 어겼는지가 그대로 부르는 쪽에 도착함
        pendingUpdateRepository.saveAndFlush(
                PlacePendingUpdate.detect(placeId, fieldName, currentValue, newValue, source));
    }
}
