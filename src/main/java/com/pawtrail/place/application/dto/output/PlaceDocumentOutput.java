package com.pawtrail.place.application.dto.output;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.provider.dto.RawDocumentView;
import java.time.LocalDateTime;

/**
 * 화면의 「근거 원문 전체 보기」가 받는 한 장입니다.
 *
 * 표시 이름을 여기서 붙입니다.
 * 수집 서비스는 코드값만 주고 그 이름을 가지고 있지 않습니다.
 * 장소 상세의 sources[] 가 이미 같은 값을 담고 있으므로
 * 같은 화면에서 뱃지와 원문 카드가 다른 모양이 되지 않게 맞춥니다.
 *
 * @param source           어느 데이터셋에서 왔는지입니다.
 * @param sourceLabel      화면에 보이는 이름입니다. 장소 상세의 출처 뱃지와 같은 값입니다.
 * @param title            소스가 부른 이름입니다.
 *                         장소 이름과 다를 수 있고 *그 차이 자체가 보여줄 것입니다.*
 * @param body             사람이 읽는 본문입니다.
 *                         비어 있을 수 있습니다. 조건과 개요가 전부 없는 건이 있습니다.
 * @param sourceModifiedAt 소스가 알려준 마지막 수정 시각입니다.
 *                         소스마다 몇 해씩 갈리며 *그것이 이 화면의 핵심입니다.*
 * @param fetchedAt        받아 온 시각입니다. 값이 낡았는지 판단하는 재료입니다.
 */
public record PlaceDocumentOutput(SourceType source,
                                  String sourceLabel,
                                  String title,
                                  String body,
                                  LocalDateTime sourceModifiedAt,
                                  LocalDateTime fetchedAt) {

    /**
     * 받은 것을 화면이 쓰는 모양으로 바꿉니다.
     *
     * 모르는 소스면 비웁니다. 예외를 던지지 않습니다.
     * 양쪽이 지금은 같은 값을 쓰지만 한쪽에만 소스가 늘 수 있고,
     * 그때 예외로 다루면 볼 수 있는 원문까지 함께 못 보게 됩니다.
     * 부르는 쪽이 비어 있는 것을 걸러 냅니다.
     */
    public static PlaceDocumentOutput from(RawDocumentView view) {
        SourceType source = toSource(view.source());
        if (source == null) {
            return null;
        }
        return new PlaceDocumentOutput(
                source,
                source.label(),
                view.title(),
                view.body(),
                view.sourceModifiedAt(),
                view.fetchedAt());
    }

    private static SourceType toSource(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SourceType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
