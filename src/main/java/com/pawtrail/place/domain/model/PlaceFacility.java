package com.pawtrail.place.domain.model;

import com.pawtrail.place.domain.enums.FacilityCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소의 편의시설입니다.
 *
 * BaseEntity 를 상속하지 않습니다.
 * 배치가 만들고 지우는 순수 연결 표라 누가 언제 만들었는지를 볼 사람이 없습니다.
 * 이 서비스에서 감사 컬럼이 없는 유일한 표입니다.
 *
 * 값을 고치는 메서드가 없습니다.
 * 편의시설은 바뀌면 지우고 다시 넣습니다. 두 컬럼이 곧 기본 키라 고칠 것이 없습니다.
 */
@Entity
@Table(name = "place_facility")
@IdClass(PlaceFacilityId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceFacility {

    @Id
    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    // enum 이름을 그대로 문자열로 담음
    // ORDINAL 은 쓰지 않음, 상수 순서를 바꾸면 이미 저장된 값의 뜻이 통째로 달라짐
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "facility_code", nullable = false, updatable = false, length = 24)
    private FacilityCode facilityCode;

    private PlaceFacility(UUID placeId, FacilityCode facilityCode) {
        this.placeId = placeId;
        this.facilityCode = facilityCode;
    }

    public static PlaceFacility create(UUID placeId, FacilityCode facilityCode) {
        if (placeId == null || facilityCode == null) {
            throw new IllegalArgumentException("placeId 와 facilityCode 는 필수입니다.");
        }
        return new PlaceFacility(placeId, facilityCode);
    }
}
