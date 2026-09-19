package com.pawtrail.place.application.dto.output;

import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.model.Place;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 다른 서비스가 받아 가는 장소 요약입니다.
 *
 * GET /internal/places?ids= 의 원소이며 user 가 카드를 조립할 때 씁니다.
 * 즐겨찾기 · 방문 기록 · 일정 · 최근 본 장소 · 하루 요약이 이 응답 하나를 나눠 씁니다.
 *
 * 필드 일곱은 부르는 쪽인 user 가 정했습니다.
 * 명세에는 누가 부르는지만 있고 무엇이 오는지가 적혀 있지 않았습니다.
 * user 의 PlaceResponse 가 이 이름을 그대로 받으므로 이름을 바꾸면 그쪽 값이 조용히 비게 됩니다.
 * 필드를 더하는 것은 괜찮습니다. user 가 모르는 필드를 무시합니다.
 *
 * 주소와 영업 상태는 담지 않습니다.
 * 카드에 주소가 없고, 폐업한 장소도 담기지만 카드에 그것을 표시할 자리가 아직 없습니다.
 * search 는 이 API 를 쓰지 않고 색인용 조회(PlaceIndexingOutput)를 씁니다.
 * 쓰는 곳이 다른 두 모양을 한 응답에 섞지 않으려고 나눴습니다.
 *
 * @param placeId     장소 식별자입니다. 부르는 쪽이 이 값으로 자기 목록과 맞춥니다.
 * @param name        장소 이름입니다. 대표 소스의 값입니다.
 * @param placeType   카테고리입니다. 카테고리 칩과 지도 마커 색이 씁니다.
 * @param imageUrl    대표 사진입니다. 없을 수 있습니다.
 * @param lat         위도입니다. 일정 화면의 지도가 씁니다.
 * @param lon         경도입니다.
 * @param supplyPoint 반려견 간식 · 용품점처럼 동선 중에 들르는 보급 지점인지입니다.
 *                    지도 범례가 따로 두는데 placeType 에는 그 값이 없어 이 플래그로 가릅니다.
 */
public record PlaceSummaryOutput(UUID placeId,
                                 String name,
                                 PlaceType placeType,
                                 String imageUrl,
                                 BigDecimal lat,
                                 BigDecimal lon,
                                 boolean supplyPoint) {

    public static PlaceSummaryOutput from(Place place) {
        return new PlaceSummaryOutput(
                place.getId(),
                place.getName(),
                place.getPlaceType(),
                place.getImageUrl(),
                place.getLat(),
                place.getLon(),
                place.isSupplyPoint());
    }
}
