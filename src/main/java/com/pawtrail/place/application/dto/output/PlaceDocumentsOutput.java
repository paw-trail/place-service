package com.pawtrail.place.application.dto.output;

import java.util.List;

/**
 * 그 장소의 원문 묶음입니다.
 *
 * 목록을 그대로 내보내지 않고 한 겹 감쌉니다.
 * 배열을 최상위로 두면 나중에 필드를 더할 때 응답 모양이 통째로 바뀌어
 * 화면이 파싱하는 코드를 고쳐야 합니다.
 *
 * @param documents 그 장소가 어느 원본에서 왔는지입니다. 없으면 빈 목록입니다.
 */
public record PlaceDocumentsOutput(List<PlaceDocumentOutput> documents) {
}
