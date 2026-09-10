package com.pawtrail.place.domain.repository;

import com.pawtrail.place.domain.model.Place;
import java.util.Optional;
import java.util.UUID;

/**
 * 장소를 저장하고 찾아오는 약속입니다.
 *
 * 이 인터페이스에는 JPA 라는 단어가 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 하는지는 infrastructure 가 정합니다.
 *
 * 지금은 최소한만 둡니다.
 * 병합 후보 조회와 배치 조회는 그 API 를 만들 때 늘립니다.
 * 미리 만들어 두면 그 형태가 맞는지는 정작 쓸 때 알게 되고,
 * 아무도 안 부르는 메서드가 남습니다.
 *
 * 하드 딜리트를 쓰지 않습니다.
 * 폐업은 status 를 CLOSED 로 바꿔 표현하며 행을 지우지 않습니다.
 * 지우면 즐겨찾기 · 방문 기록 · 후기의 참조가 끊깁니다.
 * 그래서 delete 를 두지 않았습니다.
 */
public interface PlaceRepository {

    Place save(Place place);

    Optional<Place> findById(UUID id);
}
