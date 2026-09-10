package com.pawtrail.place.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.place.domain.enums.MatchMethod;
import com.pawtrail.place.domain.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 이 장소가 어느 소스의 어느 레코드에서 왔는지입니다.
 *
 * 장소 하나에 소스가 여럿 붙습니다.
 * 장소 상세의 sources[] 와 원문 보기가 이 표를 읽습니다.
 *
 * 하드 딜리트입니다.
 * 소스 분리(DELETE /admin/places/{id}/sources/{sourceId})는 행을 실제로 지웁니다.
 * 소프트로 두면 uq_place_source(source, source_id) 에 걸려
 * 같은 소스를 다시 붙일 수 없게 되는 것이 결정적인 이유입니다.
 *
 * 이 표는 "지금 이 장소가 어느 소스로 이뤄져 있나" 를 담으므로
 * 떼어낸 소스는 그 답에 없는 것이 맞습니다.
 * 되짚을 근거가 필요하면 ingest 의 raw_document 가 원본을 그대로 갖고 있습니다.
 *
 * BaseEntity 는 그대로 상속하되 deleted_at 과 deleted_by 는 항상 null 입니다.
 */
@Entity
@Table(name = "place_source_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceSourceLink extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // place_db 안이지만 외래 키를 걸지 않음, 전 테이블 공통 규약임
    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private SourceType source;

    // 소스가 주는 식별자임
    // 공사와 고캠핑은 contentId, 행정안전부 CSV 는 관리번호임
    // 문화정보원은 식별자 컬럼이 없어 ingest 가 시설명|지번주소 로 만들어 넘김
    //
    // 폭이 200 인 이유는 실측이 상한을 보장하지 않기 때문임
    // 적재본 전수에서 가장 긴 값이 52 자였으나
    // 문화정보원 CSV 는 판이 바뀌고 시설명 칸에 설명 문장이 들어간 행이 이미 있음
    // 넘치면 ingest 청크가 통째로 롤백되어 수집이 멈춤
    @Column(name = "source_id", nullable = false, updatable = false, length = 200)
    private String sourceId;

    // place 본체를 채울 때 이 소스가 이겼는지임
    //
    // 장소당 참인 행이 반드시 하나임, uq_place_source_primary 가 강제함
    // 병합을 적재 시점에 즉시 하기로 한 결정이 이 불변식에 기대고 있음
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false, updatable = false, length = 20)
    private MatchMethod matchMethod;

    // 매칭 신뢰도임
    // COORD_ORIGINAL 은 거리로 나눠 담음, 0~50m 를 높게 50~100m 를 낮게 둠
    // 검증할 때 낮은 것부터 뽑아 눈으로 확인하기 위해서임
    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    private PlaceSourceLink(UUID placeId, SourceType source, String sourceId,
                            boolean primary, MatchMethod matchMethod, BigDecimal confidence) {
        this.placeId = placeId;
        this.source = source;
        this.sourceId = sourceId;
        this.primary = primary;
        this.matchMethod = matchMethod;
        this.confidence = confidence;
        this.linkedAt = LocalDateTime.now();
    }

    /**
     * 이 소스가 장소를 처음 만들 때 씁니다.
     *
     * 대표 소스가 되며 match_method 는 PRIMARY 입니다.
     * 신뢰도를 담지 않는 것은 견줄 대상이 없기 때문입니다.
     */
    public static PlaceSourceLink createPrimary(UUID placeId, SourceType source, String sourceId) {
        validate(placeId, source, sourceId);
        return new PlaceSourceLink(placeId, source, sourceId, true, MatchMethod.PRIMARY, null);
    }

    /**
     * 이미 있는 장소에 이 소스를 붙일 때 씁니다.
     *
     * 팩터리를 둘로 나눈 이유는 채우는 값이 다르기 때문입니다.
     * 처음 만든 소스는 언제나 대표이고 판정 방법이 PRIMARY 이며 신뢰도가 없습니다.
     * 하나로 두면 "PRIMARY 인데 대표가 아닌" 조합을 만들 수 있게 됩니다.
     */
    public static PlaceSourceLink link(UUID placeId, SourceType source, String sourceId,
                                       MatchMethod matchMethod, BigDecimal confidence) {
        validate(placeId, source, sourceId);
        if (matchMethod == null || matchMethod == MatchMethod.PRIMARY) {
            throw new IllegalArgumentException("붙이는 소스의 판정 방법은 PRIMARY 가 아니어야 합니다.");
        }
        return new PlaceSourceLink(placeId, source, sourceId, false, matchMethod, confidence);
    }

    /**
     * 대표 소스로 승격시킵니다.
     *
     * 소스 분리로 대표가 사라졌을 때 나머지 중 하나를 올리는 자리에서 씁니다.
     * 부르는 쪽이 기존 대표를 먼저 내리지 않으면 uq_place_source_primary 에 걸립니다.
     */
    public void promote() {
        this.primary = true;
    }

    /**
     * 대표에서 내립니다.
     *
     * 다른 소스를 대표로 올리기 전에 먼저 부릅니다.
     */
    public void demote() {
        this.primary = false;
    }

    private static void validate(UUID placeId, SourceType source, String sourceId) {
        if (placeId == null || source == null) {
            throw new IllegalArgumentException("placeId 와 source 는 필수입니다.");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId 는 필수입니다.");
        }
    }
}
