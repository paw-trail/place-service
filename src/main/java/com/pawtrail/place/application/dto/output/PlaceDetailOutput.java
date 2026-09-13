package com.pawtrail.place.application.dto.output;

import com.pawtrail.place.domain.enums.FacilityCode;
import com.pawtrail.place.domain.enums.PlaceStatus;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.enums.TelSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 장소 상세 화면이 받는 장소 정보입니다.
 *
 * GET /api/v1/places/{placeId} 의 응답이며 필드는 명세를 그대로 따릅니다.
 * 판정 · 후기 · 조건 충돌 · 집중률은 여기 없습니다.
 * 각 서비스가 따로 주고 프론트가 병렬로 불러 한 화면에 모읍니다.
 *
 * 매칭과 적재에만 쓰는 값은 내보내지 않습니다.
 * 괄호 별칭 · 정규화한 이름과 주소 · 지역 코드 · 좌표 출처 · 원천 분류 · 공공누리 유형 · 관리자 잠금이 그것입니다.
 *
 * @param placeId        장소 식별자입니다.
 * @param name           장소 이름입니다. 대표 소스의 값입니다.
 * @param placeType      카테고리입니다.
 * @param address        도로명 주소이며 없으면 지번 주소입니다.
 *                       공사 계열은 지번을 주지 않고 문화정보원은 지번을 주므로 둘 중 있는 것을 씁니다.
 * @param lat            위도입니다. 상세 화면의 지도 핀이 씁니다.
 * @param lon            경도입니다.
 * @param tel            전화번호입니다. 없을 수 있습니다.
 * @param telSource      전화번호를 어디서 얻었는지입니다. 번호가 없으면 null 입니다.
 * @param homepage       홈페이지입니다.
 * @param reservationUrl 예약처입니다. 캠핑장은 홈페이지와 예약처가 다른 경우가 많습니다.
 * @param imageUrl       대표 사진입니다.
 * @param overview       장소 소개입니다. 소스가 준 문장 그대로입니다.
 * @param businessHours  운영 시간 안내입니다. 시각이 아니라 안내문이라 길 수 있습니다.
 * @param closedDays     쉬는 날 안내입니다.
 * @param facilities     편의시설 코드입니다. 없으면 빈 배열이고 화면이 편의시설 섹션을 숨깁니다.
 * @param supplyPoint    동선 중에 들르는 보급 지점인지입니다.
 * @param status         영업 상태입니다. 폐업한 장소도 CLOSED 로 담겨 나갑니다.
 * @param sources        이 장소를 이룬 데이터셋입니다. 소스마다 한 번씩, 대표 순서로 담습니다.
 *                       조건 충돌 배지가 떴을 때 소스가 둘이라는 것이 보여야 이해가 됩니다.
 * @param dataBaseDate   소스가 준 데이터 기준일입니다. 장소마다 다르고 없을 수 있습니다.
 */
public record PlaceDetailOutput(UUID placeId,
                                String name,
                                PlaceType placeType,
                                String address,
                                BigDecimal lat,
                                BigDecimal lon,
                                String tel,
                                TelSource telSource,
                                String homepage,
                                String reservationUrl,
                                String imageUrl,
                                String overview,
                                String businessHours,
                                String closedDays,
                                List<FacilityCode> facilities,
                                boolean supplyPoint,
                                PlaceStatus status,
                                List<Source> sources,
                                LocalDate dataBaseDate) {

    /**
     * 장소를 이룬 데이터셋 하나입니다.
     *
     * 원문 보기(GET /api/v1/places/{placeId}/documents)와 같은 이름을 씁니다.
     * 프론트가 두 응답의 출처를 같은 모양으로 다룹니다.
     *
     * @param source      데이터셋 코드입니다.
     * @param sourceLabel 사람이 읽는 출처 이름입니다. 컬럼이 아니라 SourceType 의 코드 상수입니다.
     */
    public record Source(SourceType source, String sourceLabel) {

        public static Source of(SourceType source) {
            return new Source(source, source.label());
        }
    }
}
