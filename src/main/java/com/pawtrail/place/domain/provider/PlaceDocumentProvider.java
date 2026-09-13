package com.pawtrail.place.domain.provider;

import com.pawtrail.place.domain.provider.dto.RawDocumentView;
import java.util.List;
import java.util.UUID;

/**
 * 그 장소의 원본을 가진 곳에서 가져옵니다.
 *
 * 이 인터페이스에는 HTTP 도 서비스 이름도 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 부르는지는 infrastructure 가 정합니다.
 *
 * 실패를 값으로 돌려주지 않습니다.
 * 빈 목록으로 바꿔 주면 원문이 정말 없는 것과 지금 못 가져오는 것이 같은 모양이 됩니다.
 * 부르는 쪽이 그 둘을 갈라야 해서 실패는 예외로 알립니다.
 */
public interface PlaceDocumentProvider {

    /**
     * 그 장소가 어느 원본에서 왔는지를 가져옵니다.
     *
     * 빈 목록이 올 수 있습니다.
     * 원본을 거치지 않는 소스로만 만들어진 장소는 보여줄 것이 없습니다.
     *
     * @throws RuntimeException 가져오지 못한 경우입니다.
     */
    List<RawDocumentView> findByPlaceId(UUID placeId);
}
