package com.pawtrail.place.domain.model;

import com.pawtrail.place.domain.enums.FacilityCode;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * place_facility 의 복합 기본 키입니다.
 *
 * @IdClass 로 씁니다. @EmbeddedId 를 안 쓰는 이유는
 * 필드 접근이 한 겹 깊어져(facility.getId().getPlaceId()) 다른 엔티티와 모양이 갈리기 때문입니다.
 * user 의 DailySummaryId 가 같은 근거로 @IdClass 를 썼습니다.
 *
 * Serializable 과 equals · hashCode 는 JPA 명세가 요구합니다.
 * 없으면 영속성 컨텍스트가 같은 키를 다른 것으로 보아 조회할 때마다 새 인스턴스가 생깁니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class PlaceFacilityId implements Serializable {

    private UUID placeId;

    private FacilityCode facilityCode;

    private PlaceFacilityId(UUID placeId, FacilityCode facilityCode) {
        this.placeId = placeId;
        this.facilityCode = facilityCode;
    }

    public static PlaceFacilityId of(UUID placeId, FacilityCode facilityCode) {
        if (placeId == null || facilityCode == null) {
            throw new IllegalArgumentException("placeId 와 facilityCode 는 필수입니다.");
        }
        return new PlaceFacilityId(placeId, facilityCode);
    }
}
