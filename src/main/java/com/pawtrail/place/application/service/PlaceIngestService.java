package com.pawtrail.place.application.service;

import com.pawtrail.place.application.dto.input.PlaceDraft;
import com.pawtrail.place.application.dto.output.BulkResult;
import com.pawtrail.place.domain.enums.CoordSource;
import com.pawtrail.place.domain.enums.FacilityCode;
import com.pawtrail.place.domain.enums.MatchMethod;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.enums.TelSource;
import com.pawtrail.place.domain.event.payload.PlaceUpdatedEvent;
import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.model.PlaceFacility;
import com.pawtrail.place.domain.model.PlaceSourceLink;
import com.pawtrail.common.message.outbox.OutboxEventRecorder;
import com.pawtrail.place.domain.provider.GeocodingProvider;
import com.pawtrail.place.domain.repository.PlaceFacilityRepository;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.domain.repository.PlaceSourceLinkRepository;
import com.pawtrail.place.domain.rule.AddressNormalizer;
import com.pawtrail.place.domain.rule.CoordinateNormalizer;
import com.pawtrail.place.domain.rule.FacilityResolver;
import com.pawtrail.place.domain.rule.NameNormalizer;
import com.pawtrail.place.domain.rule.PlaceMatcher;
import com.pawtrail.place.domain.rule.PlaceMerger;
import com.pawtrail.place.domain.rule.PlaceTypeMapper;
import com.pawtrail.place.domain.rule.Sido;
import com.pawtrail.place.domain.rule.ValueCleaner;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 결과를 적재합니다.
 *
 * 한 건마다 하는 일이 다섯입니다.
 *
 * <pre>
 * 1  정규화     이름 · 주소 · 좌표 · 분류 · 값 다듬기
 * 2  판정       주소로 찾고 없으면 좌표로 찾음
 * 3  병합 or 신규
 * 4  소스 연결   place_source_link
 * 5  편의시설    place_facility 를 지우고 다시 넣음
 * </pre>
 *
 * 병합을 적재 시점에 즉시 하는 것이 중요합니다.
 * 매칭 전 상태의 행을 만들지 않으므로 place_id 가 발급된 뒤 병합으로 죽는 행이 없습니다.
 * 증분 수집에서는 즐겨찾기와 방문 기록이 그 값을 물고 있어 되돌릴 수 없습니다.
 *
 * 청크 하나가 한 트랜잭션입니다.
 * 대기 행만 REQUIRES_NEW 로 빼는 이유는 PlacePendingUpdateService 에 적어 두었습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceIngestService {

    private final PlaceRepository placeRepository;
    private final PlaceSourceLinkRepository sourceLinkRepository;
    private final PlaceFacilityRepository facilityRepository;
    private final PlacePendingUpdateService pendingUpdateService;
    private final GeocodingProvider geocodingProvider;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 청크 하나를 적재합니다.
     *
     * 건마다 결과가 다르므로 건수를 세어 돌려줍니다.
     */
    @Transactional
    public BulkResult ingest(List<PlaceDraft> drafts) {
        int created = 0;
        int merged = 0;
        int skipped = 0;
        int pending = 0;
        int pendingFailed = 0;

        for (PlaceDraft draft : drafts) {
            Outcome outcome = ingestOne(draft);
            created += outcome.created();
            merged += outcome.merged();
            skipped += outcome.skipped();
            pending += outcome.pending();
            pendingFailed += outcome.pendingFailed();
        }

        return new BulkResult(created, merged, skipped, pending, pendingFailed);
    }

    private Outcome ingestOne(PlaceDraft draft) {
        // 이미 붙어 있는 소스면 아무것도 하지 않습니다
        //
        // 재수집이 멱등이 되는 자리입니다
        // 같은 결과를 여러 번 밀어 넣어도 uq_place_source 까지 가지 않습니다
        Optional<PlaceSourceLink> existing =
                sourceLinkRepository.findBySourceAndSourceId(draft.source(), draft.sourceId());
        if (existing.isPresent()) {
            return updateExisting(draft, existing.get());
        }

        Place incoming = normalize(draft);
        if (incoming == null) {
            // 좌표를 끝내 만들지 못한 행입니다
            // lat 과 lon 이 NOT NULL 이라 넣을 값이 없습니다
            log.warn("좌표가 없어 적재하지 못했습니다: source={}, sourceId={}, name={}",
                    draft.source(), draft.sourceId(), draft.name());
            return Outcome.ofSkipped();
        }

        PlaceMatcher.Match match = findMatch(incoming);
        if (match.matched()) {
            return mergeInto(match, draft, incoming);
        }
        return createNew(draft, incoming);
    }

    /**
     * 소스가 준 값을 저장할 수 있는 형태로 다듬습니다.
     *
     * 좌표를 못 만들면 null 입니다. 부르는 쪽이 건너뜁니다.
     */
    private Place normalize(PlaceDraft draft) {
        BigDecimal[] coordinate = resolveCoordinate(draft);
        if (coordinate == null) {
            return null;
        }

        PlaceType placeType = PlaceTypeMapper.resolve(
                draft.source(), draft.lcls1(), draft.lcls2(), draft.lcls3());

        Place place = Place.create(draft.name(), placeType, coordinate[0], coordinate[1]);

        place.applyNormalized(
                NameNormalizer.normalize(draft.name()),
                NameNormalizer.extractAliases(draft.name()),
                AddressNormalizer.normalize(draft.addressRoad(), draft.addressJibun(), draft.sidoName()));

        Sido sido = AddressNormalizer.resolveSidoOnly(
                draft.addressRoad(), draft.addressJibun(), draft.sidoName());
        place.applyAddress(draft.addressRoad(), draft.addressJibun(),
                sido == null ? null : sido.code(), null);

        place.applyCoordinate(coordinate[0], coordinate[1], resolveCoordSource(draft, coordinate));
        place.applyClassification(placeType, draft.lcls1(), draft.lcls2(), draft.lcls3());

        String tel = ValueCleaner.extractPhone(draft.tel());
        place.applyContact(
                tel,
                tel == null ? null : telSourceOf(draft.source()),
                ValueCleaner.cleanUrl(draft.homepage()),
                ValueCleaner.cleanUrl(draft.reservationUrl()),
                ValueCleaner.forceHttps(draft.imageUrl()),
                draft.cpyrhtDivCd());

        place.applyDescription(draft.overview(), draft.businessHours(), draft.closedDays());
        place.applyDataBaseDate(draft.dataBaseDate());
        place.changeSupplyPoint(PlaceTypeMapper.isSupplyPoint(draft.source(), draft.lcls2()));

        return place;
    }

    /**
     * 쓸 수 있는 좌표를 만듭니다.
     *
     * 소스가 준 좌표가 대한민국 안이면 그대로 쓰고,
     * 없거나 범위 밖이면 주소로 지오코딩합니다.
     * 둘 다 안 되면 null 입니다.
     *
     * 지오코딩을 적재 중에 부르는 이유는 대상이 일곱 건뿐이기 때문입니다.
     * 별도 배치를 두는 것은 그 건수 때문에 새 장치를 만드는 것이라 과합니다.
     */
    private BigDecimal[] resolveCoordinate(PlaceDraft draft) {
        CoordinateNormalizer.Result result =
                CoordinateNormalizer.normalize(draft.lat(), draft.lon());
        if (result.usable()) {
            return new BigDecimal[]{result.lat(), result.lon()};
        }

        String address = draft.addressRoad() != null && !draft.addressRoad().isBlank()
                ? draft.addressRoad()
                : draft.addressJibun();

        return geocodingProvider.geocode(address)
                .map(c -> {
                    CoordinateNormalizer.Result checked = CoordinateNormalizer.normalize(
                            c.lat().toPlainString(), c.lon().toPlainString());
                    // 지오코딩 결과도 범위를 봅니다
                    // 카카오가 엉뚱한 값을 줄 일은 없으나 검사가 한 곳에 모여 있는 편이 낫습니다
                    return checked.usable()
                            ? new BigDecimal[]{checked.lat(), checked.lon()}
                            : null;
                })
                .orElse(null);
    }

    /**
     * 좌표가 어디서 왔는지 정합니다.
     *
     * 소스가 준 좌표를 그대로 썼으면 ingest 가 준 값을 믿고,
     * 지오코딩으로 채웠으면 GEOCODED 입니다.
     *
     * 이 값이 다음 병합의 임계값을 가르므로 정확해야 합니다.
     */
    private CoordSource resolveCoordSource(PlaceDraft draft, BigDecimal[] resolved) {
        CoordinateNormalizer.Result original =
                CoordinateNormalizer.normalize(draft.lat(), draft.lon());
        if (!original.usable()) {
            return CoordSource.GEOCODED;
        }
        if (draft.coordSource() == null || draft.coordSource().isBlank()) {
            return CoordSource.ORIGINAL;
        }
        try {
            return CoordSource.valueOf(draft.coordSource());
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 좌표 출처입니다: {}", draft.coordSource());
            return CoordSource.ORIGINAL;
        }
    }

    /**
     * 같은 장소를 찾습니다. 주소가 먼저이고 좌표가 나중입니다.
     *
     * 적재본에서 병합 쌍 148 개 중 113 개가 주소 단계에서 붙었습니다.
     * 76% 라 대부분 여기서 끝나 좌표 조회를 부르지 않습니다.
     */
    private PlaceMatcher.Match findMatch(Place incoming) {
        PlaceMatcher.Match byAddress = PlaceMatcher.matchByAddress(
                incoming, placeRepository.findByAddressNormalized(incoming.getAddressNormalized()));
        if (byAddress.matched()) {
            return byAddress;
        }
        return PlaceMatcher.matchByCoordinate(
                incoming,
                placeRepository.findNearby(
                        incoming.getLat(), incoming.getLon(), PlaceMatcher.SEARCH_METERS));
    }

    private Outcome createNew(PlaceDraft draft, Place incoming) {
        Place saved = placeRepository.save(incoming);
        sourceLinkRepository.save(
                PlaceSourceLink.createPrimary(saved.getId(), draft.source(), draft.sourceId()));
        replaceFacilities(saved.getId(), draft);
        return Outcome.ofCreated();
    }

    /**
     * 이미 있는 장소에 새 소스를 붙입니다.
     *
     * 잠긴 장소면 본체를 고치지 않고 대기 행으로 쌓습니다.
     * 규칙을 한 문장으로 하면 "생성은 배치가 자유롭게, 수정과 삭제는 사람의 확인을 거쳐서" 입니다.
     */
    private Outcome mergeInto(PlaceMatcher.Match match, PlaceDraft draft, Place incoming) {
        Place target = match.place();

        sourceLinkRepository.save(PlaceSourceLink.link(
                target.getId(), draft.source(), draft.sourceId(),
                match.method(), match.confidence()));

        if (target.isAdminLocked()) {
            PendingCount counted = recordPending(target, incoming, draft.source());
            return Outcome.ofMerged(counted.pending(), counted.failed());
        }

        // 대표를 가져가야 하면 is_primary 만 옮깁니다
        //
        // 값을 다시 쓰지 않습니다
        // 먼저 들어온 소스가 채운 것을 덮어쓰면 적재 순서에 따라 결과가 달라집니다
        if (shouldTakeOver(target, draft.source())) {
            takeOver(target.getId(), draft.source(), draft.sourceId());
        }

        target.fillEmptyFrom(incoming);
        placeRepository.save(target);
        replaceFacilities(target.getId(), draft);
        publishUpdated(target.getId());
        return Outcome.ofMerged();
    }

    /**
     * 이미 붙어 있는 소스가 다시 들어온 경우입니다.
     *
     * 재수집이라 값이 바뀌었을 수 있습니다.
     * 잠긴 장소면 대기 행으로 쌓고, 아니면 빈 칸을 채웁니다.
     */
    private Outcome updateExisting(PlaceDraft draft, PlaceSourceLink link) {
        Optional<Place> found = placeRepository.findById(link.getPlaceId());
        if (found.isEmpty()) {
            log.warn("연결된 장소가 없습니다: placeId={}", link.getPlaceId());
            return Outcome.ofSkipped();
        }

        Place target = found.get();
        Place incoming = normalize(draft);
        if (incoming == null) {
            return Outcome.ofSkipped();
        }

        if (target.isAdminLocked()) {
            PendingCount counted = recordPending(target, incoming, draft.source());
            return Outcome.ofMerged(counted.pending(), counted.failed());
        }

        target.fillEmptyFrom(incoming);
        placeRepository.save(target);
        replaceFacilities(target.getId(), draft);
        publishUpdated(target.getId());
        return Outcome.ofMerged();
    }

    /**
     * 잠긴 장소에서 달라진 값을 대기 행으로 쌓습니다.
     *
     * 비교 대상을 일곱으로 둡니다.
     *
     * 이름과 도로명 주소를 넣은 이유가 있습니다.
     * 이름이 바뀌면 name_normalized 도 바뀌어 다음 병합 판정이 달라지는데,
     * 잠겨서 반영이 안 되면 그 장소만 옛 이름으로 남아 새 소스와 안 붙습니다.
     * 주소도 같습니다. address_normalized 가 매칭 일 순위 키입니다.
     *
     * 소개문과 분류는 넣지 않습니다.
     * overview 는 current_value 와 new_value 의 폭인 500 자를 넘겨
     * 잠긴 장소마다 대기 행 삽입이 실패합니다.
     * 분류는 관리자가 화면에서 판단할 값이 아닙니다.
     *
     * 명세가 든 예시는 전화번호 하나뿐이고 목록을 못 박지 않았습니다.
     * 관리자 화면이 읽을 만한 크기와 병합에 영향을 주는 값을 기준으로 골랐습니다.
     */
    private PendingCount recordPending(Place target, Place incoming, SourceType source) {
        int pending = 0;
        int failed = 0;

        String[][] pairs = {
                {"name", target.getName(), incoming.getName()},
                {"address_road", target.getAddressRoad(), incoming.getAddressRoad()},
                {"tel", target.getTel(), incoming.getTel()},
                {"homepage", target.getHomepage(), incoming.getHomepage()},
                {"reservation_url", target.getReservationUrl(), incoming.getReservationUrl()},
                {"image_url", target.getImageUrl(), incoming.getImageUrl()},
                {"business_hours", target.getBusinessHours(), incoming.getBusinessHours()},
        };

        for (String[] pair : pairs) {
            if (!changed(pair[1], pair[2])) {
                continue;
            }
            boolean ok = pendingUpdateService.record(
                    target.getId(), pair[0], pair[1], pair[2], source);
            if (ok) {
                pending++;
            } else {
                failed++;
            }
        }

        return new PendingCount(pending, failed);
    }

    /**
     * 값이 실제로 달라졌는지 봅니다.
     *
     * 새 값이 비어 있으면 달라진 것으로 보지 않습니다.
     * 소스가 값을 안 준 것이지 "지우라" 는 뜻이 아닙니다.
     */
    private boolean changed(String current, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return false;
        }
        return !incoming.equals(current);
    }

    private boolean shouldTakeOver(Place target, SourceType incoming) {
        return sourceLinkRepository.findAllByPlaceId(target.getId()).stream()
                .filter(PlaceSourceLink::isPrimary)
                .findFirst()
                .map(primary -> PlaceMerger.shouldTakeOver(incoming, primary.getSource()))
                .orElse(true);
    }

    /**
     * 대표를 옮깁니다.
     *
     * 기존 대표를 먼저 내리지 않으면 uq_place_source_primary 에 걸립니다.
     */
    private void takeOver(java.util.UUID placeId, SourceType source, String sourceId) {
        sourceLinkRepository.findAllByPlaceId(placeId).stream()
                .filter(PlaceSourceLink::isPrimary)
                .forEach(link -> {
                    link.demote();
                    sourceLinkRepository.save(link);
                });
        sourceLinkRepository.findBySourceAndSourceId(source, sourceId)
                .ifPresent(link -> {
                    link.promote();
                    sourceLinkRepository.save(link);
                });
    }

    /**
     * 편의시설을 지우고 다시 넣습니다.
     *
     * 고치는 메서드가 없는 이유는 두 컬럼이 곧 기본 키라 고칠 것이 없기 때문입니다.
     *
     * 삭제와 저장이 같은 트랜잭션 안에 있어야 합니다.
     * 나뉘면 지우기만 하고 넣기가 실패했을 때 편의시설이 통째로 빕니다.
     */
    private void replaceFacilities(java.util.UUID placeId, PlaceDraft draft) {
        List<FacilityCode> codes = FacilityResolver.resolve(
                draft.source(), draft.parking(),
                draft.posblFcltyCl(), draft.sbrsCl(), draft.resveCl());
        if (codes.isEmpty()) {
            return;
        }
        facilityRepository.deleteAllByPlaceId(placeId);
        for (FacilityCode code : codes) {
            facilityRepository.save(PlaceFacility.create(placeId, code));
        }
    }

    /**
     * 전화번호의 출처입니다.
     *
     * 지금은 소스에서 그대로 유추합니다.
     * 카카오로 번호를 보완하는 기능이 생기면 그때 KAKAO 가 들어옵니다.
     */
    private TelSource telSourceOf(SourceType source) {
        return switch (source) {
            case PET_TOUR -> TelSource.PET_TOUR;
            case GOCAMPING -> TelSource.GOCAMPING;
            case CULTURE_CSV -> TelSource.CULTURE_CSV;
            case MOIS_VET -> TelSource.MOIS_VET;
        };
    }

    /**
     * 장소가 바뀌었음을 알립니다. search 가 받아 색인을 다시 만듭니다.
     *
     * 새로 만들 때는 부르지 않습니다.
     * 명세가 "변경 시" 로 규정했고 초기 적재가 만 칠천 건이라
     * 신규까지 발행하면 아직 소비자가 없는 토픽에 그만큼이 쌓입니다.
     *
     * 잠긴 장소도 부르지 않습니다. 본체를 안 고쳤으므로 바뀐 것이 없습니다.
     *
     * 같은 트랜잭션 안에서 기록합니다.
     * OutboxEventRecorder 가 @Transactional(MANDATORY) 라 트랜잭션 없이 부르면 즉시 예외입니다.
     * place 를 고친 것과 이벤트 행이 한 트랜잭션이라
     * "값은 바뀌었는데 알림이 안 나간" 상태가 원천 차단됩니다.
     */
    private void publishUpdated(java.util.UUID placeId) {
        outboxEventRecorder.record(new PlaceUpdatedEvent(placeId));
    }

    private record PendingCount(int pending, int failed) {
    }

    /**
     * 적재 한 건의 결과입니다.
     *
     * 팩터리 이름에 of 를 붙이는 이유가 있습니다.
     * record 는 컴포넌트마다 같은 이름의 접근자를 자동으로 만드는데,
     * 정적 메서드를 같은 이름으로 두면 그 자리를 침범해 컴파일이 막힙니다.
     */
    private record Outcome(int created, int merged, int skipped, int pending, int pendingFailed) {

        static Outcome ofCreated() {
            return new Outcome(1, 0, 0, 0, 0);
        }

        static Outcome ofMerged() {
            return new Outcome(0, 1, 0, 0, 0);
        }

        static Outcome ofMerged(int pending, int pendingFailed) {
            return new Outcome(0, 1, 0, pending, pendingFailed);
        }

        static Outcome ofSkipped() {
            return new Outcome(0, 0, 1, 0, 0);
        }
    }
}
