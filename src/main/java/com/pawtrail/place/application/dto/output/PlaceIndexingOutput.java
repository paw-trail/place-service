package com.pawtrail.place.application.dto.output;

import com.pawtrail.place.domain.enums.FacilityCode;
import com.pawtrail.place.domain.enums.PlaceStatus;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.model.Place;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 검색 서비스가 색인을 세울 때 받아 가는 장소입니다.
 *
 * GET /internal/places/indexing 의 원소입니다.
 * 사용자 서비스가 받는 요약(PlaceSummaryOutput)과 모양을 나눴습니다.
 * 요약은 카드 일곱 칸만 담고, 여기는 검색이 좁히고 보여 주는 데 쓰는 값을 담습니다.
 * 한 모양에 섞으면 카드 목록을 부를 때마다 소개문 같은 긴 값이 따라가고,
 * 한쪽의 필요로 칸을 바꿀 때마다 다른 쪽이 함께 흔들립니다.
 *
 * 반려동물 동반 조건은 한 칸도 없습니다.
 * 그것은 policy 의 값이고 검색 색인에 담지 않기로 했습니다.
 *
 * @param placeId      장소 식별자입니다.
 * @param name         대표 소스의 이름입니다. 카드 제목과 이름 검색에 씁니다.
 * @param nameAlias    괄호 별칭입니다. 이름 검색에 함께 씁니다. 없으면 빈 목록입니다.
 * @param placeType    서비스 카테고리입니다. 동물병원(VET)도 담습니다.
 * @param addressRoad  도로명 주소입니다.
 * @param addressJibun 지번 주소입니다. 도로명이 없는 장소는 이것이 표시 주소가 됩니다.
 * @param sidoCode     시도 코드(법정동 두 자리)입니다. 지역 필터에 씁니다.
 * @param sigunguName  시군구 이름입니다. 지역 필터와 지역 목록에 씁니다. 세종은 비어 있습니다.
 * @param lat          위도입니다. 거리 검색에 씁니다.
 * @param lon          경도입니다.
 * @param facilities   편의시설입니다. 선언 순서로 담고 없으면 빈 목록입니다.
 * @param status       영업 상태입니다. 폐업한 장소도 담고 검색이 상태로 거릅니다.
 * @param imageUrl     대표 사진입니다. 없을 수 있습니다.
 * @param overview     소개문입니다. 검색어가 문장 속 낱말에 걸리게 하는 데 씁니다.
 * @param dataBaseDate 소스가 밝힌 데이터 기준일입니다. 검색 카드가 그대로 보여 줍니다.
 * @param updatedAt    이 행을 마지막으로 고친 시각입니다.
 *                     재색인과 이벤트 처리가 겹칠 때 받는 쪽이 더 새 값만 덮어쓰는 데 씁니다.
 */
public record PlaceIndexingOutput(UUID placeId,
                                  String name,
                                  List<String> nameAlias,
                                  PlaceType placeType,
                                  String addressRoad,
                                  String addressJibun,
                                  String sidoCode,
                                  String sigunguName,
                                  BigDecimal lat,
                                  BigDecimal lon,
                                  List<FacilityCode> facilities,
                                  PlaceStatus status,
                                  String imageUrl,
                                  String overview,
                                  LocalDate dataBaseDate,
                                  LocalDateTime updatedAt) {

    public static PlaceIndexingOutput from(Place place, List<FacilityCode> facilities) {
        return new PlaceIndexingOutput(
                place.getId(),
                place.getName(),
                place.getNameAlias() == null ? List.of() : place.getNameAlias(),
                place.getPlaceType(),
                place.getAddressRoad(),
                place.getAddressJibun(),
                place.getSidoCode(),
                place.getSigunguName(),
                place.getLat(),
                place.getLon(),
                facilities == null ? List.of() : facilities,
                place.getStatus(),
                place.getImageUrl(),
                place.getOverview(),
                place.getDataBaseDate(),
                place.getUpdatedAt());
    }
}
