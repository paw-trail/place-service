package com.pawtrail.place.domain.provider.dto;

import java.time.LocalDateTime;

/**
 * 수집 서비스가 준 원문 하나입니다.
 *
 * 소스를 문자열로 받습니다.
 *
 * 우리 열거값으로 바로 받으면 그쪽에만 소스가 늘었을 때 읽는 시점에 실패하고,
 * 그 실패가 목록 전체를 무너뜨립니다.
 * 모르는 값이 와도 일단 받아 두고 어떻게 다룰지는 응답으로 바꾸는 자리에서 정합니다.
 *
 * 표시 이름이 없습니다. 그쪽은 그 값을 가지고 있지 않고 우리가 가지고 있습니다.
 * 원본도 없습니다. 기계용 필드를 빼고 사람이 읽는 문장만 조립해 둔 것을 받습니다.
 *
 * @param source           어느 데이터셋에서 왔는지입니다.
 * @param title            소스가 부른 이름입니다. 장소 이름과 다를 수 있습니다.
 * @param body             사람이 읽는 본문입니다. 없을 수 있습니다.
 * @param sourceModifiedAt 소스가 알려준 마지막 수정 시각입니다.
 * @param fetchedAt        받아 온 시각입니다.
 */
public record RawDocumentView(String source,
                              String title,
                              String body,
                              LocalDateTime sourceModifiedAt,
                              LocalDateTime fetchedAt) {
}
