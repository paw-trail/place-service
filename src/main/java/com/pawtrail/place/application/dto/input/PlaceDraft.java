package com.pawtrail.place.application.dto.input;

import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.rule.CoordinateNormalizer;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 적재 한 건입니다. 소스가 준 값을 그대로 담습니다.
 *
 * 정규화하기 전의 원본입니다.
 * 이름과 주소와 좌표를 다듬는 일, 분류를 옮기는 일, 전화번호를 뽑는 일은
 * 모두 place 가 합니다. ingest 는 파싱만 합니다.
 *
 * 그렇게 가른 이유가 셋입니다.
 *   정규화 규칙이 전부 place/domain/rule 에 있습니다
 *   규칙이 바뀌면 place 가 혼자 다시 돌리면 됩니다. 반대면 재수집이 필요합니다
 *   받는 필드가 적을수록 ingest 파서와 어긋날 위험이 작습니다
 *
 * 좌표가 문자열인 이유는 소수 열 자리가 오기 때문입니다.
 * 실수로 받으면 그 시점에 정밀도가 흔들립니다.
 */
public record PlaceDraft(
        SourceType source,
        String sourceId,

        String name,
        String addressRoad,
        String addressJibun,

        // 주소 첫 토큰이 시도가 아닐 때 쓰는 폴백입니다
        // 고캠핑의 doNm 과 문화정보원의 시도 명칭이 여기 옵니다
        // 적재본에서 주소에 시도가 빠진 두 행이 이것으로 풀렸습니다
        String sidoName,

        String lat,
        String lon,
        String coordSource,

        // 소스마다 이름이 다른 원천 분류를 한 형태로 받습니다
        //   공사        lclsSystm1 · 2 · 3
        //   고캠핑       induty 를 lcls1 에
        //   문화정보원   카테고리3 을 lcls2 에
        String lcls1,
        String lcls2,
        String lcls3,

        // 다듬기 전 원본입니다
        // 안내 문구와 앵커 태그와 http 는 place 의 ValueCleaner 가 처리합니다
        String tel,
        String homepage,
        String imageUrl,
        String cpyrhtDivCd,
        String overview,
        String businessHours,
        String closedDays,
        String reservationUrl,
        LocalDate dataBaseDate,

        // 편의시설 원본입니다
        // 판별을 place 가 하는 이유는 place_type 매핑을 여기서 하기 때문입니다
        String parking,
        String posblFcltyCl,
        String sbrsCl,
        String resveCl,

        // 지오코딩으로 미리 채워 둔 좌표입니다
        //
        // 요청에는 없는 값이고 적재 1 패스가 넣습니다.
        // 외부 호출을 데이터베이스 트랜잭션 밖에서 끝내기 위한 자리입니다.
        // 소스가 준 좌표가 쓸 만하거나 지오코딩도 실패하면 null 입니다.
        BigDecimal geocodedLat,
        BigDecimal geocodedLon) {

    /**
     * 지오코딩 결과를 채운 사본을 만듭니다.
     *
     * record 라 값을 바꿀 수 없으므로 새로 만듭니다.
     * 원본을 그대로 두는 편이 1 패스와 2 패스가 같은 것을 보게 해 줍니다.
     */
    public PlaceDraft withGeocoded(BigDecimal foundLat, BigDecimal foundLon) {
        return new PlaceDraft(
                source, sourceId, name, addressRoad, addressJibun, sidoName,
                lat, lon, coordSource, lcls1, lcls2, lcls3,
                tel, homepage, imageUrl, cpyrhtDivCd, overview,
                businessHours, closedDays, reservationUrl, dataBaseDate,
                parking, posblFcltyCl, sbrsCl, resveCl,
                foundLat, foundLon);
    }

    /**
     * 지오코딩이 필요한 건인지 봅니다.
     *
     * 소스가 준 좌표를 쓸 수 있으면 부를 이유가 없습니다.
     * 적재본에서 이 조건에 걸리는 것이 아홉 건이었습니다.
     */
    public boolean needsGeocoding() {
        return !CoordinateNormalizer.normalize(lat, lon).usable();
    }

    /**
     * 지오코딩에 넘길 주소입니다. 도로명이 없으면 지번을 씁니다.
     */
    public String geocodingAddress() {
        return addressRoad != null && !addressRoad.isBlank() ? addressRoad : addressJibun;
    }
}
