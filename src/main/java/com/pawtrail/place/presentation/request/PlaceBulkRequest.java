package com.pawtrail.place.presentation.request;

import com.pawtrail.place.application.dto.input.PlaceDraft;
import com.pawtrail.place.domain.enums.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * ingest 가 수집 결과를 넘길 때 보내는 것입니다.
 *
 * 명세에 요청 본문이 한 줄도 없어 place 가 정했습니다.
 * ingest 는 파싱만 하고 정규화는 place 가 합니다.
 *
 * 청크로 나눠 보냅니다.
 * 한 번에 만 칠천 건을 보내면 요청 본문이 수십 MB 가 되고
 * 도중에 끊기면 어디까지 들어갔는지 알 수 없습니다.
 */
public record PlaceBulkRequest(

        @NotEmpty(message = "items 는 비어 있을 수 없습니다.")
        @Size(max = 1000, message = "한 번에 1000건까지 보낼 수 있습니다.")
        @Valid
        List<Item> items) {

    /**
     * 적재 한 건입니다.
     *
     * 검증을 최소한만 둡니다.
     * 소스가 주는 값이라 우리가 고칠 수 없고,
     * 형식이 이상하면 정규화가 걸러 내거나 skipped 로 빠집니다.
     * 여기서 막으면 청크 전체가 400 이 되어 멀쩡한 999 건까지 못 들어갑니다.
     */
    public record Item(

            @NotNull(message = "source 는 필수입니다.")
            SourceType source,

            @NotBlank(message = "sourceId 는 필수입니다.")
            @Size(max = 200, message = "sourceId 는 200자를 넘을 수 없습니다.")
            String sourceId,

            @NotBlank(message = "name 은 필수입니다.")
            String name,

            String addressRoad,
            String addressJibun,

            // 주소 첫 토큰이 시도가 아닐 때 쓰는 폴백입니다
            // 고캠핑의 doNm 과 문화정보원의 시도 명칭이 여기 옵니다
            String sidoName,

            // 문자열인 이유는 소수 열 자리가 오기 때문입니다
            // 실수로 받으면 그 시점에 정밀도가 흔들립니다
            String lat,
            String lon,

            // ORIGINAL · CONVERTED
            // GEOCODED 는 place 가 직접 채우므로 여기 오지 않습니다
            String coordSource,

            String lcls1,
            String lcls2,
            String lcls3,

            // 다듬기 전 원본입니다
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
            String parking,
            String posblFcltyCl,
            String sbrsCl,
            String resveCl) {

        public PlaceDraft toDraft() {
            // 마지막 둘은 지오코딩 결과 자리입니다
            // 요청에는 없는 값이고 적재 1 단계가 채웁니다
            return new PlaceDraft(
                    source, sourceId, name, addressRoad, addressJibun, sidoName,
                    lat, lon, coordSource, lcls1, lcls2, lcls3,
                    tel, homepage, imageUrl, cpyrhtDivCd, overview,
                    businessHours, closedDays, reservationUrl, dataBaseDate,
                    parking, posblFcltyCl, sbrsCl, resveCl,
                    null, null);
        }
    }

    public List<PlaceDraft> toDrafts() {
        return items.stream().map(Item::toDraft).toList();
    }
}
