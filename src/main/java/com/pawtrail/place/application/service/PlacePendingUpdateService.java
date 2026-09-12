package com.pawtrail.place.application.service;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import com.pawtrail.place.domain.repository.PlacePendingUpdateRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
     * 만든 결과입니다.
     *
     * 셋으로 가른 이유는 중복이 실패가 아니기 때문입니다.
     * 같은 값이 이미 있으면 만들 필요가 없었던 것이므로
     * 응답의 실패 건수에 세면 관리자가 고칠 것이 있는 줄로 읽습니다.
     */
    public enum Result {

        // 새로 만듦
        SAVED,

        // 같은 값이 이미 있어 만들지 않음
        DUPLICATE,

        // 만들지 못함
        FAILED
    }

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
     * 중복은 실패와 가릅니다.
     * 부르는 쪽이 같은 값이 있는지 먼저 보지만 그 검사와 이 저장 사이에
     * 다른 트랜잭션이 끼어들 수 있어 부분 UNIQUE 인덱스가 마지막에 막습니다.
     * 그때 나는 예외는 "만들 필요가 없었다" 는 뜻이지 고칠 것이 생겼다는 뜻이 아닙니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result record(UUID placeId, String fieldName,
                         String currentValue, String newValue, SourceType source) {
        try {
            // saveAndFlush 인 이유는 예외가 나는 시점 때문입니다
            //
            // save 만 부르면 실제 INSERT 가 커밋 시점으로 밀려
            // 그때 터진 예외는 이 try 블록을 이미 빠져나온 뒤라 잡히지 않습니다
            // 그러면 REQUIRES_NEW 로 뺀 뜻이 없어지고 바깥 트랜잭션까지 함께 죽습니다
            pendingUpdateRepository.saveAndFlush(
                    PlacePendingUpdate.detect(placeId, fieldName, currentValue, newValue, source));
            return Result.SAVED;
        } catch (DataIntegrityViolationException e) {
            // 같은 값이 이미 있음
            //
            // uq_place_pending_unresolved 가 막은 것임
            // 컬럼 폭을 넘겨도 같은 예외가 나오나 부르는 쪽이 이미 길이를 보고 거름
            log.debug("같은 대기 값이 이미 있습니다: placeId={}, field={}", placeId, fieldName);
            return Result.DUPLICATE;
        } catch (Exception e) {
            log.warn("대기 행을 만들지 못했습니다: placeId={}, field={}", placeId, fieldName, e);
            return Result.FAILED;
        }
    }
}
