package com.pawtrail.place.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.place.domain.enums.PendingStatus;
import com.pawtrail.place.domain.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 잠긴 장소에 대해 수집 배치가 발견했으나 반영하지 못한 값입니다.
 *
 * 규칙을 한 문장으로 하면
 * "생성은 배치가 자유롭게, 수정과 삭제는 사람의 확인을 거쳐서" 입니다.
 *
 * 판단 주체가 place 라는 점이 중요합니다.
 * ingest 가 POST /internal/places/bulk 를 부르면
 * place 가 잠긴 행을 건너뛰고 이 행을 만듭니다. 서비스 경계를 넘지 않습니다.
 */
@Entity
@Table(name = "place_pending_update")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlacePendingUpdate extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    // 어느 필드가 다른지임, tel · status · homepage 등
    // report.field_name 과 폭을 맞춰 둠, 두 표가 하는 일이 같음
    @Column(name = "field_name", nullable = false, updatable = false, length = 40)
    private String fieldName;

    @Column(name = "current_value", updatable = false, length = 500)
    private String currentValue;

    @Column(name = "new_value", updatable = false, length = 500)
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private SourceType source;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private PendingStatus status;

    // 처리한 관리자와 처리 시각임, 미처리면 둘 다 null
    //
    // report 에는 reviewed_by 와 reviewed_at 이 있는데
    // 같은 성격의 관리자 처리인 여기만 기록이 없었음
    // 자동 처리와 사람 판단이 갈리는 자리에는 기록을 남긴다는 원칙을 따름
    @Column(name = "resolved_by", length = 45)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    private PlacePendingUpdate(UUID placeId, String fieldName, String currentValue,
                               String newValue, SourceType source) {
        this.placeId = placeId;
        this.fieldName = fieldName;
        this.currentValue = currentValue;
        this.newValue = newValue;
        this.source = source;
        this.detectedAt = LocalDateTime.now();
        this.status = PendingStatus.PENDING;
    }

    public static PlacePendingUpdate detect(UUID placeId, String fieldName,
                                            String currentValue, String newValue,
                                            SourceType source) {
        if (placeId == null || source == null) {
            throw new IllegalArgumentException("placeId 와 source 는 필수입니다.");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName 은 필수입니다.");
        }
        return new PlacePendingUpdate(placeId, fieldName, currentValue, newValue, source);
    }

    /**
     * 관리자가 승인했습니다. 부르는 쪽이 place 에 값을 반영합니다.
     *
     * 반영까지 여기서 하지 않는 이유는 엔티티가 다른 엔티티를 고치게 되기 때문입니다.
     */
    public void approve(String resolvedBy) {
        resolve(PendingStatus.APPROVED, resolvedBy);
    }

    /**
     * 관리자가 반려했습니다. place 는 그대로 둡니다.
     */
    public void reject(String resolvedBy) {
        resolve(PendingStatus.REJECTED, resolvedBy);
    }

    private void resolve(PendingStatus next, String resolvedBy) {
        if (this.status != PendingStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 대기 값입니다.");
        }
        if (resolvedBy == null || resolvedBy.isBlank()) {
            throw new IllegalArgumentException("처리한 관리자가 필요합니다.");
        }
        this.status = next;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }
}
