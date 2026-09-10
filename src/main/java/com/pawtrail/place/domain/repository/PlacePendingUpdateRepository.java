package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.PlacePendingUpdate;
import java.util.Optional;
import java.util.UUID;

/**
 * 반영 대기 값을 다루는 약속입니다.
 *
 * 관리자 화면이 읽는 목록 조회는 그 API 를 만들 때 늘립니다.
 * 지금은 적재가 쌓는 것과 단건 조회까지만 둡니다.
 */
public interface PlacePendingUpdateRepository {

    PlacePendingUpdate save(PlacePendingUpdate pendingUpdate);

    Optional<PlacePendingUpdate> findById(UUID id);
}
