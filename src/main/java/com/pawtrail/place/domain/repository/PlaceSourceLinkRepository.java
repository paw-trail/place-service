package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.PlaceSourceLink;
import com.pawtrail.place.domain.enums.SourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 장소와 소스의 연결을 다루는 약속입니다.
 *
 * 하드 딜리트이므로 지울 때 delete 를 씁니다.
 * 소프트로 두면 uq_place_source 에 걸려 같은 소스를 다시 붙일 수 없게 됩니다.
 */
public interface PlaceSourceLinkRepository {

    PlaceSourceLink save(PlaceSourceLink link);

    Optional<PlaceSourceLink> findById(UUID id);

    void delete(PlaceSourceLink link);

    /**
     * 지우고 그 자리에서 데이터베이스로 내보냅니다.
     *
     * 대표 소스를 뗄 때 씁니다.
     * 떼어낸 뒤 남은 것 중 하나를 대표로 올리는데, 그 순서가 뒤집히면 안 됩니다.
     *
     * 하이버네이트는 모아 둔 작업을 내보낼 때 UPDATE 를 DELETE 보다 먼저 실행합니다.
     * delete 만 부르면 승격 UPDATE 가 먼저 나가 그 순간 대표가 둘이 되고
     * uq_place_source_primary 에 걸립니다.
     *
     * 지우기를 먼저 내보내면 인덱스가 비어 있는 상태에서 승격이 나갑니다.
     */
    void deleteAndFlush(PlaceSourceLink link);

    // 그 소스 레코드가 이미 어느 장소에 붙어 있는지 찾음
    //
    // 재수집이 멱등이 되는 근거임
    // 같은 수집 결과를 여러 번 밀어 넣어도 이 조회가 먼저 걸러
    // uq_place_source 까지 가지 않음
    Optional<PlaceSourceLink> findBySourceAndSourceId(SourceType source, String sourceId);

    // 그 장소에 붙은 소스를 전부 돌려줌
    //
    // 장소 상세의 sources[] 와 원문 보기가 씀
    // 소스 분리로 대표가 사라졌을 때 승격할 후보를 고르는 자리에서도 씀
    List<PlaceSourceLink> findAllByPlaceId(UUID placeId);
}
