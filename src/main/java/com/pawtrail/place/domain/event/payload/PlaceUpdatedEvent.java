package com.pawtrail.place.domain.event.payload;

import com.pawtrail.common.message.DomainEvent;
import java.util.UUID;

/**
 * 장소 정보가 바뀌었음을 알리는 이벤트입니다.
 *
 * search 가 받아 색인을 다시 만듭니다.
 *
 * payload 에 식별자 하나만 담습니다.
 * 받는 쪽이 GET /internal/places?ids= 로 다시 읽으므로 값을 실을 이유가 없습니다.
 * 명세가 account.created 만 값을 나르고 나머지 다섯은 식별자만 싣는다고 정해 두었습니다.
 * DTO 결합을 피하기 위해서입니다.
 * 값을 실으면 place 의 컬럼이 바뀔 때마다 소비자가 함께 바뀝니다.
 *
 * 새로 만들 때는 발행하지 않습니다.
 * 명세가 "변경 시" 로 규정했고, 초기 적재가 만 칠천 건이라
 * 신규까지 발행하면 아직 소비자가 없는 토픽에 그만큼이 쌓입니다.
 * search 의 초기 색인은 POST /admin/search/reindex 가 따로 있습니다.
 *
 * @param placeId 장소 식별자입니다.
 */
public record PlaceUpdatedEvent(UUID placeId) implements DomainEvent {

    // 아래 셋은 봉투를 만들 때만 쓰이고 payload 에는 실리지 않음
    // DomainEvent 가 @JsonIgnore 를 선언해 두었으므로 구현체가 그대로 물려받음

    @Override
    public String getTopic() {
        // infra 의 create-topics.sh 에 같은 이름이 있어야 함
        // 토픽 자동 생성을 꺼 두었으므로 없으면 발행이 실패함
        return "place.updated";
    }

    @Override
    public String getAggregateType() {
        return "Place";
    }

    @Override
    public String getAggregateId() {
        // 파티션 키가 되어 같은 장소에 대한 이벤트의 순서를 보장함
        return placeId.toString();
    }
}
