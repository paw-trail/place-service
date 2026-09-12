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
import com.pawtrail.place.domain.repository.PlaceFacilityRepository;
import com.pawtrail.place.domain.repository.PlacePendingUpdateRepository;
import com.pawtrail.place.domain.repository.PlaceRepository;
import com.pawtrail.place.domain.repository.PlaceSourceDetachRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    // 처리 전 대기 값이 중복으로 쌓이지 않게 막는 제약임
    //
    // V25 에서 만든 부분 UNIQUE 인덱스의 이름임
    private static final String PENDING_UNIQUE_CONSTRAINT = "uq_place_pending_unresolved";

    private final PlaceRepository placeRepository;
    private final PlaceSourceLinkRepository sourceLinkRepository;
    private final PlaceSourceDetachRepository sourceDetachRepository;
    private final PlaceFacilityRepository facilityRepository;
    private final PlacePendingUpdateService pendingUpdateService;
    private final PlacePendingUpdateRepository pendingUpdateRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 청크 하나를 적재합니다.
     *
     * 건마다 결과가 다르므로 건수를 세어 돌려줍니다.
     */
    /**
     * 준비가 끝난 청크를 저장합니다.
     *
     * 지오코딩은 여기서 하지 않습니다.
     * 외부 호출이 데이터베이스 커넥션을 붙잡으면 청크를 여러 개 동시에 보낼 때
     * 커넥션 풀이 그만큼 빨리 마릅니다.
     * CoordinatePrefiller 가 트랜잭션 밖에서 먼저 채워 주며,
     * 그 둘을 잇는 것은 PlaceBulkService 입니다.
     *
     * 짝의 좌표를 여전히 우선합니다.
     * 미리 채워 둔 지오코딩 값은 짝이 없을 때 쓰는 폴백입니다.
     */
    @Transactional
    public BulkResult ingest(List<PlaceDraft> drafts) {
        int created = 0;
        int merged = 0;
        int skipped = 0;
        int pending = 0;
        int pendingFailed = 0;

        // 어느 소스 레코드가 어느 장소가 됐는지를 모음
        //
        // 건너뛴 것은 담기지 않아 요청보다 짧을 수 있음
        List<BulkResult.SourceLink> links = new ArrayList<>(drafts.size());

        for (PlaceDraft draft : drafts) {
            Outcome outcome = ingestOne(draft);
            created += outcome.created();
            merged += outcome.merged();
            skipped += outcome.skipped();
            pending += outcome.pending();
            pendingFailed += outcome.pendingFailed();

            if (outcome.placeId() != null) {
                links.add(new BulkResult.SourceLink(
                        draft.source(), draft.sourceId(), outcome.placeId()));
            }
        }

        return new BulkResult(created, merged, skipped, pending, pendingFailed, links);
    }

    private Outcome ingestOne(PlaceDraft draft) {
        // 이미 붙어 있는 소스면 값만 갱신합니다
        //
        // 재수집이 멱등이 되는 자리입니다
        // 같은 결과를 여러 번 밀어 넣어도 uq_place_source 까지 가지 않습니다
        Optional<PlaceSourceLink> existing =
                sourceLinkRepository.findBySourceAndSourceId(draft.source(), draft.sourceId());
        if (existing.isPresent()) {
            return updateExisting(draft, existing.get());
        }

        String nameNormalized = NameNormalizer.normalize(draft.name());
        String addressNormalized = AddressNormalizer.normalize(
                draft.addressRoad(), draft.addressJibun(), draft.sidoName());

        // 관리자가 이 소스 레코드를 떼어낸 장소임
        //
        // 한 번만 읽어 아래 두 매칭 단계에 함께 씀
        // 주소에만 걸면 좌표로 되돌아옴
        // 같은 건물이면 좌표가 몇 미터 안이라 ST_DWithin 반경에 그대로 들어옴
        //
        // 이 조회가 적재 건마다 돌지만 인덱스를 타는 데다
        // 이 표는 관리자가 손댄 만큼만 커서 대개 비어 있음
        List<UUID> detached =
                sourceDetachRepository.findDetachedPlaceIds(draft.source(), draft.sourceId());

        // 주소로 먼저 찾습니다. 좌표가 없어도 됩니다
        //
        // 주소가 병합 일 순위 키인데 좌표가 없다는 이유로 이 판정을 못 하면 순서가 거꾸로입니다.
        // 소스가 준 좌표가 망가진 행이 주소로는 짝을 찾을 수 있는 경우가 실제로 있었습니다.
        // 기흥레스피아호수공원이 공사에서는 좌표가 필리핀 앞바다로 오는데
        // 문화정보원에 같은 도로명 주소로 정상 좌표가 있었습니다.
        PlaceMatcher.Match byAddress = PlaceMatcher.matchByAddress(
                nameNormalized,
                exclude(placeRepository.findByAddressNormalized(addressNormalized), detached));

        BigDecimal[] coordinate = resolveCoordinate(draft, byAddress);
        if (coordinate == null) {
            // 좌표를 끝내 만들지 못한 행입니다
            // lat 과 lon 이 NOT NULL 이라 넣을 값이 없습니다
            log.warn("좌표가 없어 적재하지 못했습니다: source={}, sourceId={}, name={}",
                    draft.source(), draft.sourceId(), draft.name());
            return Outcome.ofSkipped();
        }

        String tooLong = tooLongField(draft);
        if (tooLong != null) {
            // 넘치는 값을 그대로 넣으면 INSERT 가 실패하고 청크 전체가 롤백됩니다
            // 그 건만 건너뛰면 나머지가 다 들어가고 응답의 skipped 로 드러납니다
            log.warn("값이 컬럼 폭을 넘어 적재하지 못했습니다: source={}, sourceId={}, field={}",
                    draft.source(), draft.sourceId(), tooLong);
            return Outcome.ofSkipped();
        }

        Place incoming = buildPlace(draft, coordinate, nameNormalized, addressNormalized,
                byAddress.matched() ? byAddress.place() : null);

        if (byAddress.matched()) {
            return mergeInto(byAddress, draft, incoming);
        }

        PlaceMatcher.Match byCoordinate = PlaceMatcher.matchByCoordinate(
                incoming,
                exclude(placeRepository.findNearby(
                        incoming.getLat(), incoming.getLon(), PlaceMatcher.SEARCH_METERS), detached));
        if (byCoordinate.matched()) {
            return mergeInto(byCoordinate, draft, incoming);
        }
        return createNew(draft, incoming);
    }

    /**
     * 관리자가 떼어낸 장소를 후보에서 뺍니다.
     *
     * 이것이 없으면 분리가 다음 적재에 되돌려집니다.
     * 연결 행을 실제로 지우므로 그 소스 레코드는 처음 보는 것이 되어 매처를 다시 타고,
     * 정규화 주소가 그대로라 같은 장소로 돌아옵니다.
     *
     * 걸러 낸 뒤 갈 곳이 없으면 새 장소가 됩니다.
     * 관리자가 다른 장소라고 판단해 뗀 것이라 그 장소를 없애면 판단과 어긋납니다.
     * 좌표가 없어 넣을 값이 없는 경우와는 성격이 다릅니다.
     */
    private List<Place> exclude(List<Place> candidates, List<UUID> detachedPlaceIds) {
        if (detachedPlaceIds.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(place -> !detachedPlaceIds.contains(place.getId()))
                .toList();
    }

    /**
     * 컬럼 폭을 넘는 필드가 있는지 봅니다.
     *
     * 다듬은 뒤의 값으로 봅니다.
     * 전화번호는 안내 문구에서 번호만 뽑아 내므로 원본이 길어도 문제가 없고,
     * 홈페이지와 이미지 주소는 text 라 폭이 없습니다.
     */
    private String tooLongField(PlaceDraft draft) {
        return Place.tooLongField(
                draft.name(), draft.addressRoad(), draft.addressJibun(),
                ValueCleaner.extractPhone(draft.tel()),
                draft.lcls1(), draft.lcls2(), draft.lcls3(),
                draft.businessHours(), draft.closedDays(), draft.cpyrhtDivCd());
    }

    /**
     * 소스가 준 값을 저장할 수 있는 형태로 다듬습니다.
     *
     * 좌표와 정규화 값은 이미 만들어 둔 것을 받습니다.
     * 주소 판정을 좌표보다 먼저 하기 위해 그것들을 바깥에서 계산하기 때문입니다.
     *
     * matchedTarget 은 주소로 붙은 상대입니다.
     * 좌표 출처를 그쪽에서 물려받기 위해 넘깁니다. 아래 resolveCoordSource 를 보십시오.
     */
    private Place buildPlace(PlaceDraft draft, BigDecimal[] coordinate,
                             String nameNormalized, String addressNormalized,
                             Place matchedTarget) {

        PlaceType placeType = PlaceTypeMapper.resolve(
                draft.source(), draft.lcls1(), draft.lcls2(), draft.lcls3());

        Place place = Place.create(draft.name(), placeType, coordinate[0], coordinate[1]);

        place.applyNormalized(
                nameNormalized,
                NameNormalizer.extractAliases(draft.name()),
                addressNormalized);

        Sido sido = AddressNormalizer.resolveSidoOnly(
                draft.addressRoad(), draft.addressJibun(), draft.sidoName());
        place.applyAddress(draft.addressRoad(), draft.addressJibun(),
                sido == null ? null : sido.code(), null);

        place.applyCoordinate(coordinate[0], coordinate[1],
                resolveCoordSource(draft, matchedTarget));
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
     * 순서가 셋입니다.
     *   소스가 준 좌표가 대한민국 안이면 그대로 씁니다
     *   주소로 이미 짝을 찾았으면 그 장소의 좌표를 물려받습니다
     *   둘 다 아니면 주소로 지오코딩합니다
     *
     * 가운데 단계가 중요합니다.
     * 짝이 이미 있으면 그쪽 좌표가 답이므로 카카오를 부를 이유가 없습니다.
     * 호출이 줄고, 카카오가 못 찾는 주소여도 병합이 됩니다.
     * 실제로 멀쩡한 도로명인데 카카오가 못 찾는 행이 있었습니다.
     *
     * 병합되면 이 좌표는 place 본체에 반영되지 않습니다.
     * 짝의 값을 그대로 복사한 것이라 fillEmptyFrom 이 바꿀 것도 없습니다.
     */
    private BigDecimal[] resolveCoordinate(PlaceDraft draft, PlaceMatcher.Match byAddress) {
        CoordinateNormalizer.Result result =
                CoordinateNormalizer.normalize(draft.lat(), draft.lon());
        if (result.usable()) {
            return new BigDecimal[]{result.lat(), result.lon()};
        }

        // 짝이 있으면 그쪽 좌표가 답입니다
        // 1 패스에서 지오코딩을 해 두었더라도 짝의 값을 우선합니다
        // 소스가 준 주소로 만든 좌표보다 이미 자리 잡은 장소의 좌표가 정확합니다
        if (byAddress.matched()) {
            Place target = byAddress.place();
            return new BigDecimal[]{target.getLat(), target.getLon()};
        }

        // 1 패스가 채워 둔 값입니다
        if (draft.geocodedLat() != null && draft.geocodedLon() != null) {
            CoordinateNormalizer.Result checked = CoordinateNormalizer.normalize(
                    draft.geocodedLat().toPlainString(), draft.geocodedLon().toPlainString());
            // 지오코딩 결과도 범위를 봅니다
            // 카카오가 엉뚱한 값을 줄 일은 없으나 검사가 한 곳에 모여 있는 편이 낫습니다
            if (checked.usable()) {
                return new BigDecimal[]{checked.lat(), checked.lon()};
            }
        }
        return null;
    }

    /**
     * 좌표가 어디서 왔는지 정합니다.
     *
     * 소스가 준 좌표를 그대로 썼으면 ingest 가 준 값을 믿습니다.
     * 주소로 붙은 짝에게서 좌표를 물려받았으면 그쪽의 출처를 함께 물려받습니다.
     * 지오코딩으로 채웠으면 GEOCODED 입니다.
     *
     * 이 값이 다음 병합의 임계값을 가르므로 정확해야 합니다.
     * 물려받은 좌표에 GEOCODED 를 붙이면 그 장소가 이후 판정에서
     * 실제보다 넓은 반경으로 다뤄집니다.
     */
    private CoordSource resolveCoordSource(PlaceDraft draft, Place matchedTarget) {
        CoordinateNormalizer.Result original =
                CoordinateNormalizer.normalize(draft.lat(), draft.lon());
        if (!original.usable()) {
            return matchedTarget != null ? matchedTarget.getCoordSource() : CoordSource.GEOCODED;
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

    private Outcome createNew(PlaceDraft draft, Place incoming) {
        Place saved = placeRepository.save(incoming);
        sourceLinkRepository.save(
                PlaceSourceLink.createPrimary(saved.getId(), draft.source(), draft.sourceId()));
        replaceFacilities(saved.getId(), draft);
        return Outcome.ofCreated(saved.getId());
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
            return Outcome.ofMerged(counted.pending(), counted.failed(), target.getId());
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
        return Outcome.ofMerged(target.getId());
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
        // 이미 붙어 있는 소스라 짝은 target 입니다
        // 좌표가 망가졌으면 그쪽 값을 물려받습니다
        BigDecimal[] coordinate = resolveCoordinate(
                draft, PlaceMatcher.Match.matched(target));
        if (coordinate == null) {
            return Outcome.ofSkipped();
        }
        Place incoming = buildPlace(draft, coordinate,
                NameNormalizer.normalize(draft.name()),
                AddressNormalizer.normalize(
                        draft.addressRoad(), draft.addressJibun(), draft.sidoName()),
                target);

        if (target.isAdminLocked()) {
            PendingCount counted = recordPending(target, incoming, draft.source());
            return Outcome.ofMerged(counted.pending(), counted.failed(), target.getId());
        }

        target.fillEmptyFrom(incoming);
        placeRepository.save(target);
        replaceFacilities(target.getId(), draft);
        publishUpdated(target.getId());
        return Outcome.ofMerged(target.getId());
    }

    /**
     * 잠긴 장소에서 달라진 값을 대기 행으로 쌓습니다.
     *
     * 비교 대상을 여덟으로 둡니다.
     *
     * 이름을 넣은 이유가 있습니다.
     * 수집은 이름을 바꾸지 않습니다. fillEmptyFrom 이 name 을 일부러 건너뛰기 때문입니다.
     * 그래서 소스가 다른 이름을 보내도 place 에는 영영 반영되지 않고,
     * 관리자가 PATCH 로 고치는 것이 유일한 길입니다.
     * 그 사실을 관리자에게 알리는 자리가 여기입니다.
     *
     * 주소는 도로명과 지번을 함께 봅니다.
     * 둘이 한 덩어리라 도로명만 반영하면 지번이 지워지거나 반쪽 주소에서 정규화 값이 나옵니다.
     * 지번을 함께 쌓아 두면 승인할 때 묶어 넘길 수 있고,
     * 지번 대기 값이 없다는 것이 곧 그 소스가 지번을 안 바꿨다는 근거가 됩니다.
     * address_normalized 가 매칭 일 순위 키라 어느 쪽이 틀려도 다음 병합이 달라집니다.
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
                {"address_jibun", target.getAddressJibun(), incoming.getAddressJibun()},
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
            // 같은 값이 이미 대기 중이거나 반려된 적이 있으면 건너뜀
            //
            // 소스가 값을 고치지 않는 한 같은 차이가 수집마다 발견됨
            // 그때마다 행을 만들면 목록에 같은 값이 여러 줄 뜨고
            // 반려한 것도 다시 올라와 관리자가 같은 판단을 되풀이하게 됨
            //
            // 승인된 것은 보지 않음
            // 승인되면 place 의 값이 새 값이 되어 위 changed 가 이미 거름
            if (pendingUpdateRepository.existsUnresolved(target.getId(), pair[0], pair[2])) {
                continue;
            }
            // 저장은 별도 트랜잭션에서 하고 실패는 여기서 가름
            //
            // 잡는 자리가 그 트랜잭션 밖이어야 함
            // 안에서 잡으면 하이버네이트가 되돌릴 수밖에 없다고 표시한 뒤라
            // 커밋 단계에서 UnexpectedRollbackException 이 나고 이 트랜잭션까지 죽음
            if (!record(target.getId(), pair[0], pair[1], pair[2], source)) {
                failed++;
                continue;
            }
            pending++;
        }

        return new PendingCount(pending, failed);
    }

    /**
     * 대기 행 하나를 만들고 결과를 알려 줍니다.
     *
     * 참이면 새로 만들었거나 이미 같은 값이 있어 만들 필요가 없었던 것입니다.
     * 거짓이면 만들지 못한 것이며 응답의 실패 건수로 셉니다.
     *
     * 같은 값이 이미 있는 것을 실패로 세지 않습니다.
     * 위에서 미리 보고 걸렀는데도 여기 걸렸다면
     * 그 검사와 저장 사이에 다른 트랜잭션이 끼어든 것이고,
     * 그때는 부분 UNIQUE 인덱스가 막아 결과적으로 바라던 상태가 됩니다.
     * 관리자가 고칠 것이 생긴 것이 아니므로 실패로 세면 목록을 잘못 읽게 합니다.
     *
     * 제약 이름으로 가릅니다.
     * 같은 예외가 컬럼 폭을 넘겼을 때도 나오는데 그쪽은 관리자가 알아야 할 실패입니다.
     * 메시지를 뒤지지 않는 것은 그 문구가 데이터베이스와 판에 따라 달라지기 때문입니다.
     */
    private boolean record(UUID placeId, String fieldName,
                           String currentValue, String newValue, SourceType source) {
        try {
            pendingUpdateService.record(placeId, fieldName, currentValue, newValue, source);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) {
                log.debug("같은 대기 값이 이미 있습니다: placeId={}, field={}", placeId, fieldName);
                return true;
            }
            log.warn("대기 행을 만들지 못했습니다: placeId={}, field={}", placeId, fieldName, e);
            return false;
        } catch (Exception e) {
            log.warn("대기 행을 만들지 못했습니다: placeId={}, field={}", placeId, fieldName, e);
            return false;
        }
    }

    /**
     * 처리 전 대기 값의 중복 제약을 어긴 것인지 봅니다.
     *
     * 하이버네이트가 제약 이름을 담아 주고 스프링이 그것을 감싸 던집니다.
     * 이름이 다르거나 원인이 그 예외가 아니면 거짓입니다.
     * 폭 초과처럼 관리자가 알아야 할 실패를 중복으로 삼키지 않기 위해서입니다.
     *
     * 정적이며 이 패키지에서 보입니다. 검사가 예외를 만들어 그대로 부를 수 있어야 합니다.
     */
    static boolean isDuplicate(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        return cause instanceof ConstraintViolationException violation
                && PENDING_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
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
    private void takeOver(UUID placeId, SourceType source, String sourceId) {
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
    private void replaceFacilities(UUID placeId, PlaceDraft draft) {
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
    private void publishUpdated(UUID placeId) {
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
    /**
     * 한 건을 처리한 결과입니다.
     *
     * 식별자를 함께 담습니다.
     * 부르는 쪽이 자기 표의 place_id 를 채우려면 건수만으로는 알 수 없습니다.
     *
     * 건너뛴 경우에만 비어 있습니다.
     * 장소를 만들지 못했으므로 알려 줄 식별자가 없습니다.
     * 잠긴 장소는 값을 바꾸지 못했을 뿐 이미 있는 장소에 붙은 것이라 식별자가 있습니다.
     */
    private record Outcome(int created, int merged, int skipped, int pending, int pendingFailed,
                           UUID placeId) {

        static Outcome ofCreated(UUID placeId) {
            return new Outcome(1, 0, 0, 0, 0, placeId);
        }

        static Outcome ofMerged(UUID placeId) {
            return new Outcome(0, 1, 0, 0, 0, placeId);
        }

        static Outcome ofMerged(int pending, int pendingFailed, UUID placeId) {
            return new Outcome(0, 1, 0, pending, pendingFailed, placeId);
        }

        static Outcome ofSkipped() {
            return new Outcome(0, 0, 1, 0, 0, null);
        }
    }
}
