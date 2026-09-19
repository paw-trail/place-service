package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.PlaceFacility;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 장소의 편의시설을 다루는 약속입니다.
 *
 * 값을 고치는 메서드가 없습니다.
 * 편의시설은 바뀌면 지우고 다시 넣습니다.
 * 두 컬럼이 곧 기본 키라 고칠 것이 없기 때문입니다.
 */
public interface PlaceFacilityRepository {

    PlaceFacility save(PlaceFacility facility);

    List<PlaceFacility> findAllByPlaceId(UUID placeId);

    /**
     * 여러 장소의 편의시설을 한 번에 찾습니다. 색인용 조회가 씁니다.
     *
     * 장소마다 따로 물으면 왕복이 장소 수만큼 생깁니다.
     * 순서는 정하지 않으므로 부르는 쪽이 장소별로 묶고 늘어놓습니다.
     */
    List<PlaceFacility> findAllByPlaceIdIn(Collection<UUID> placeIds);

    // 그 장소의 편의시설을 전부 지움
    //
    // 다시 넣기 전에 부름
    // 반환은 지운 행 수임, 적재 로그에 몇 건이 바뀌었는지 남기기 위함임
    int deleteAllByPlaceId(UUID placeId);
}
