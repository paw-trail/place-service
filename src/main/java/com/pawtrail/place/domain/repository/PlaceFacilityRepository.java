package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.PlaceFacility;
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

    // 그 장소의 편의시설을 전부 지움
    //
    // 다시 넣기 전에 부름
    // 반환은 지운 행 수임, 적재 로그에 몇 건이 바뀌었는지 남기기 위함임
    int deleteAllByPlaceId(UUID placeId);
}
