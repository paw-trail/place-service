package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.PlacePendingUpdate;
import java.util.Optional;
import java.util.UUID;

/**
 * 반영 대기 값을 다루는 약속입니다.
 *
 * 관리자 화면이 읽는 목록 조회는 그 API 를 만들 때 늘립니다.
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
}
