package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlaceSourceDetach;
import java.util.List;
import java.util.UUID;

/**
 * 관리자가 떼어낸 소스를 다루는 약속입니다.
 *
 * 지우는 메서드가 없습니다.
 * 여기서 행을 지우는 것은 관리자의 분리 판단을 무르는 일이고,
 * 분리를 되돌리는 기능은 만들지 않기로 했습니다.
 */
public interface PlaceSourceDetachRepository {

    PlaceSourceDetach save(PlaceSourceDetach detach);

    /**
     * 이 소스 레코드가 떼어진 장소를 모두 찾습니다.
     *
     * 적재가 매칭 후보를 거를 때 씁니다.
     * 같은 소스 레코드가 여러 장소에서 떼어질 수 있어 목록입니다.
     * 처음에 A 에서 떼면 다음 적재에 별도 장소 B 가 생기는데,
     * B 에서 또 떼면 A 와 B 둘 다 남습니다.
     *
     * 장소 전체가 아니라 식별자만 돌려줍니다.
     * 후보에서 빼는 데에는 식별자면 충분하고, 이 조회가 적재 건마다 돕니다.
     */
    List<UUID> findDetachedPlaceIds(SourceType source, String sourceId);
}
