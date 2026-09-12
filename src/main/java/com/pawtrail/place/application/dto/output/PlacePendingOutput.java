package com.pawtrail.place.application.dto.output;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자가 보는 반영 대기 값 한 줄입니다.
 *
 * 수집이 잠긴 장소에서 발견했으나 반영하지 못한 값입니다.
 * 관리자가 이 줄을 보고 승인하거나 반려합니다.
 *
 * 필드는 명세를 그대로 따릅니다.
 *
 * status 를 담지 않습니다.
 * 목록이 처리하지 않은 것만 보여주므로 모든 줄이 같은 값이 되어 알려 줄 것이 없습니다.
 * 명세의 응답에도 그 필드가 없습니다.
 *
 * placeName 은 이 표에 없는 값이라 place 에서 가져옵니다.
 * 식별자만 보여주면 관리자가 어느 장소인지 알 수 없어 매번 다시 조회하게 됩니다.
 *
 * @param pendingId    대기 값 식별자입니다. 승인과 반려가 이 값을 씁니다.
 * @param placeId      어느 장소인지입니다.
 * @param placeName    그 장소의 지금 이름입니다.
 * @param fieldName    무엇이 다른지입니다. 컬럼 이름을 그대로 씁니다.
 * @param currentValue 지금 place 에 있는 값입니다.
 * @param newValue     수집이 가져온 값입니다. 승인하면 이 값이 들어갑니다.
 * @param source       그 값을 준 데이터셋입니다.
 * @param detectedAt   수집이 차이를 발견한 시각입니다. 목록은 이 값의 최신순입니다.
 */
public record PlacePendingOutput(UUID pendingId,
                                 UUID placeId,
                                 String placeName,
                                 String fieldName,
                                 String currentValue,
                                 String newValue,
                                 SourceType source,
                                 LocalDateTime detectedAt) {

    public static PlacePendingOutput of(PlacePendingUpdate pending, String placeName) {
        return new PlacePendingOutput(
                pending.getId(),
                pending.getPlaceId(),
                placeName,
                pending.getFieldName(),
                pending.getCurrentValue(),
                pending.getNewValue(),
                pending.getSource(),
                pending.getDetectedAt());
    }
}
