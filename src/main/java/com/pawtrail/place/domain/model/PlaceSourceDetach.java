package com.pawtrail.place.domain.model;

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
 * 관리자가 떼어낸 소스입니다.
 *
 * 감사 기록이 아니라 규칙입니다.
 * 적재가 돌 때마다 매처가 이 표를 읽어 후보에서 그 장소를 걸러냅니다.
 * 행이 사라지면 기록이 사라지는 것이 아니라 분리가 풀립니다.
 *
 * 이 표가 없으면 분리가 다음 적재에 되돌려집니다.
 * 연결 행을 하드 딜리트하므로 그 소스 레코드는 다음 수집에서 처음 보는 것이 되고,
 * 주소가 그대로라 매처가 같은 장소로 다시 붙입니다.
 *
 * BaseEntity 를 상속하지 않습니다.
 * 한 번 쓰고 고치지 않는 표라 updated 와 deleted 계열 넷이 죽은 컬럼이 됩니다.
 * 특히 deleted_at 이 있으면 이력을 지울 수 있는 것으로 읽히는데,
 * 여기서 행을 지우는 것은 관리자의 분리 판단을 무르는 일입니다.
 * policy 의 정정 이력 표를 같은 이유로 미상속으로 두기로 했습니다.
 *
 * 되돌리는 메서드를 두지 않았습니다.
 * 분리를 되돌리는 기능은 만들지 않기로 했습니다.
 * 잘못 뗐으면 그 소스는 다음 적재에서 별도 장소로 생기므로 데이터가 사라지지는 않습니다.
 */
@Entity
@Table(name = "place_source_detach")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceSourceDetach {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // 떼어낸 장소임
    //
    // 이 표의 단위는 소스 레코드가 아니라 장소와 소스의 조합임
    // "이 소스를 이 장소에 붙이지 말라" 는 뜻이라 다른 장소에는 붙을 수 있어야 함
    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private SourceType source;

    @Column(name = "source_id", nullable = false, updatable = false, length = 200)
    private String sourceId;

    // 뗀 관리자임
    //
    // BaseEntity 를 안 쓰므로 JPA Auditing 이 채우지 않음
    // 컨트롤러가 인증 주체에서 꺼내 서비스로 넘김
    @Column(name = "detached_by", nullable = false, updatable = false, length = 45)
    private String detachedBy;

    @Column(name = "detached_at", nullable = false, updatable = false)
    private LocalDateTime detachedAt;

    private PlaceSourceDetach(UUID placeId, SourceType source, String sourceId, String detachedBy) {
        this.placeId = placeId;
        this.source = source;
        this.sourceId = sourceId;
        this.detachedBy = detachedBy;
        this.detachedAt = LocalDateTime.now();
    }

    /**
     * 분리 기록을 남깁니다.
     *
     * 연결 행을 지우는 것과 한 트랜잭션 안에서 이뤄져야 합니다.
     * 연결만 지우고 이 행이 안 남으면 다음 적재에 그대로 되붙습니다.
     */
    public static PlaceSourceDetach of(UUID placeId, SourceType source,
                                       String sourceId, String detachedBy) {
        if (placeId == null || source == null) {
            throw new IllegalArgumentException("placeId 와 source 는 필수입니다.");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId 는 필수입니다.");
        }
        if (detachedBy == null || detachedBy.isBlank()) {
            throw new IllegalArgumentException("detachedBy 는 필수입니다.");
        }
        return new PlaceSourceDetach(placeId, source, sourceId, detachedBy);
    }
}
