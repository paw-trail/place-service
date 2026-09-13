package com.pawtrail.place.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.place.application.dto.output.PlaceDetailOutput;
import com.pawtrail.place.application.dto.output.PlaceDocumentOutput;
import com.pawtrail.place.application.dto.output.PlaceDocumentsOutput;
import com.pawtrail.place.application.dto.output.PlaceSummaryOutput;
import com.pawtrail.place.domain.enums.FacilityCode;
import com.pawtrail.place.domain.exception.PlaceErrorCode;
import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.model.PlaceFacility;
import com.pawtrail.place.domain.model.PlaceSourceLink;
import com.pawtrail.place.domain.provider.PlaceDocumentProvider;
import com.pawtrail.place.domain.provider.dto.RawDocumentView;
import com.pawtrail.place.domain.repository.PlaceFacilityRepository;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.domain.repository.PlaceSourceLinkRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소를 읽기만 하는 서비스입니다.
 *
 * 적재(PlaceBulkService · PlaceIngestService)와 나눠 둔 것은 받는 것이 달라서입니다.
 * 적재는 소스 원본을 받아 정규화하고 병합하고, 여기는 placeId 를 받아 있는 것을 돌려줍니다.
 *
 * 쓰기를 하지 않습니다.
 * 메인 「인기 급상승」 조회수도 여기서 올리지 않습니다.
 * 최근 본 장소를 place 가 기록하지 않고 프론트가 따로 부르게 한 것과 같은 이유로
 * 조회 핫패스에 쓰기를 끼워 넣지 않으며, 누가 올릴지는 search 를 만들 때 정합니다.
 *
 * 캐시를 두지 않습니다.
 * 두 조회 모두 기본 키로 찾으므로 싸고, 가장 자주 불리는 검색은 색인 복제로 여기를 거치지 않습니다.
 * 캐시를 두면 값이 바뀌는 자리마다 비우기를 챙겨야 하는데,
 * 빠뜨리면 관리자가 고친 값이 오류 없이 옛 값으로 보입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceQueryService {

    // 없는 식별자를 로그에 몇 개까지 적을지임
    // 전부 적으면 없는 id 가 가득한 목록 하나에 로그 한 줄이 끝없이 길어짐
    private static final int MISSING_LOG_LIMIT = 5;

    private final PlaceRepository placeRepository;
    private final PlaceDocumentProvider placeDocumentProvider;
    private final PlaceSourceLinkRepository placeSourceLinkRepository;
    private final PlaceFacilityRepository placeFacilityRepository;

    /**
     * 여러 장소를 한 번에 돌려줍니다.
     *
     * 없는 식별자는 결과에서 빠질 뿐 오류가 아닙니다. 전부 없으면 빈 목록입니다.
     * user 는 결과에 없는 것을 "그 장소가 사라졌다" 로 읽고 그 카드만 건너뜁니다.
     * 여기서 404 를 내면 user 가 그것을 "물어보지 못했다" 로 받아 목록 전체를 실패시킵니다.
     *
     * 영업 상태로 거르지 않습니다.
     * 폐업한 장소도 행이 있으므로 담습니다. 빼면 user 가 사라진 장소로 보고 카드를 조용히 지웁니다.
     *
     * 없는 식별자가 있으면 경고를 남깁니다.
     * user 도 남기지만 여기는 부르는 서비스를 전부 보는 자리라
     * 식별자가 어긋나는 일이 생기면 여기서 먼저 드러납니다.
     *
     * 중복과 null 은 걸러 냅니다. 결과는 요청한 순서를 따릅니다.
     */
    @Transactional(readOnly = true)
    public List<PlaceSummaryOutput> getSummaries(Collection<UUID> placeIds) {

        List<UUID> ids = placeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        Map<UUID, Place> found = placeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        List<PlaceSummaryOutput> result = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();
        for (UUID id : ids) {
            Place place = found.get(id);
            if (place == null) {
                missing.add(id);
                continue;
            }
            result.add(PlaceSummaryOutput.from(place));
        }

        if (!missing.isEmpty()) {
            log.warn("요청한 장소 중 없는 것이 있습니다: 요청 {}건 · 찾음 {}건 · 없는 id {}",
                    ids.size(), result.size(),
                    missing.subList(0, Math.min(missing.size(), MISSING_LOG_LIMIT)));
        }

        return result;
    }

    /**
     * 장소 하나의 상세를 돌려줍니다.
     *
     * 없으면 PLACE_NOT_FOUND 입니다.
     * 폐업한 장소는 없는 것이 아니라 status 가 CLOSED 로 나갑니다.
     */
    @Transactional(readOnly = true)
    public PlaceDetailOutput getDetail(UUID placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));

        return new PlaceDetailOutput(
                place.getId(),
                place.getName(),
                place.getPlaceType(),
                addressOf(place),
                place.getLat(),
                place.getLon(),
                place.getTel(),
                place.getTelSource(),
                place.getHomepage(),
                place.getReservationUrl(),
                place.getImageUrl(),
                place.getOverview(),
                place.getBusinessHours(),
                place.getClosedDays(),
                facilitiesOf(placeId),
                place.isSupplyPoint(),
                place.getStatus(),
                sourcesOf(placeId),
                place.getDataBaseDate());
    }

    // 도로명이 있으면 도로명, 없으면 지번을 씀
    //
    // 명세 응답의 주소 칸이 하나라 둘 중 하나를 골라야 함
    // 공사 계열은 지번을 하나도 주지 않고 문화정보원은 지번을 주므로
    // 도로명을 먼저 보되 비었으면 지번으로 채움 — V20 이 지번 컬럼을 둔 이유가 그 폴백임
    private static String addressOf(Place place) {
        String road = place.getAddressRoad();
        if (road != null && !road.isBlank()) {
            return road;
        }
        return place.getAddressJibun();
    }

    // 이 장소를 이룬 데이터셋을 소스마다 한 번씩 담음
    //
    // 같은 소스 안의 중복도 병합했으므로 한 장소에 같은 소스가 둘 붙은 경우가 있음
    // 여기는 "어느 데이터셋으로 이뤄진 장소인가" 를 보여주는 자리라 한 번만 담고,
    // 레코드마다 따로 보여줘야 하는 것은 원문 보기가 맡음
    //
    // 순서는 SourceType 선언 순서를 따름
    // 그 순서가 병합의 대표 순서(PlaceMerger)와 같아 대표 소스가 앞에 옴
    private List<PlaceDetailOutput.Source> sourcesOf(UUID placeId) {
        return placeSourceLinkRepository.findAllByPlaceId(placeId).stream()
                .map(PlaceSourceLink::getSource)
                .distinct()
                .sorted()
                .map(PlaceDetailOutput.Source::of)
                .toList();
    }

    // 편의시설 코드를 FacilityCode 선언 순서로 담음
    // 순서를 정해 두지 않으면 조회할 때마다 배지 줄의 순서가 바뀔 수 있음
    private List<FacilityCode> facilitiesOf(UUID placeId) {
        return placeFacilityRepository.findAllByPlaceId(placeId).stream()
                .map(PlaceFacility::getFacilityCode)
                .sorted()
                .toList();
    }

    /**
     * 그 장소가 어느 원본에서 왔는지를 돌려줍니다.
     *
     * 장소가 있는지 먼저 봅니다.
     * 없는 장소로 원본을 물으면 상대가 빈 목록을 주는데,
     * 그러면 "장소가 없어서 없는 것" 이 "지금 못 가져온 것" 과 섞입니다.
     * 같은 화면의 상세가 없는 식별자에 404 를 내므로 결도 맞춥니다.
     *
     * 모르는 소스는 걸러 냅니다.
     * 양쪽이 지금은 같은 값을 쓰지만 한쪽에만 소스가 늘 수 있고,
     * 그때 화면을 통째로 죽이면 볼 수 있는 원문까지 못 보게 됩니다.
     *
     * 가져오지 못하면 예외가 그대로 올라갑니다.
     * 빈 목록으로 바꾸면 원문이 정말 없는 것과 구분이 사라집니다.
     */
    public PlaceDocumentsOutput getDocuments(UUID placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new CustomException(PlaceErrorCode.PLACE_NOT_FOUND);
        }

        List<RawDocumentView> views = placeDocumentProvider.findByPlaceId(placeId);

        List<PlaceDocumentOutput> documents = views.stream()
                .map(PlaceDocumentOutput::from)
                .filter(Objects::nonNull)
                .toList();

        if (documents.size() < views.size()) {
            // 우리가 모르는 소스가 섞여 있음
            //
            // 화면은 나머지로 뜨나 우리가 알아채야 하는 상태임
            log.warn("모르는 소스를 걸러 냈습니다. placeId={} 받은 것={} 내보낸 것={}",
                    placeId, views.size(), documents.size());
        }

        return new PlaceDocumentsOutput(documents);
    }
}
