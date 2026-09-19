package com.pawtrail.place.presentation.controller;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.place.application.dto.output.BulkResult;
import com.pawtrail.place.application.dto.output.PlaceIndexingOutput;
import com.pawtrail.place.application.dto.output.PlaceSummaryOutput;
import com.pawtrail.place.application.service.PlaceBulkService;
import com.pawtrail.place.application.service.PlaceQueryService;
import com.pawtrail.place.presentation.request.PlaceBulkRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스끼리 부르는 장소 API 입니다.
 *
 * 게이트웨이가 /internal 을 라우팅하지 않으므로 바깥에서 닿지 않습니다.
 * 그것이 이 경로의 유일한 보호막입니다.
 * 서비스 간 인증을 두지 않는 것은 프로젝트가 처음부터 정한 전제입니다.
 */
@Slf4j
@RestController
@RequestMapping("/internal/places")
@RequiredArgsConstructor
public class InternalPlaceController {

    private final PlaceBulkService placeBulkService;
    private final PlaceQueryService placeQueryService;

    /**
     * 여러 장소를 한 번에 돌려줍니다.
     *
     * user 가 즐겨찾기 · 방문 기록 · 일정 · 최근 본 장소 · 하루 요약의 카드를 조립할 때 부르고,
     * 명세상 notification · review 도 부릅니다. search 는 아래 색인용 조회를 씁니다.
     *
     * 없는 식별자는 결과에서 빠질 뿐 오류가 아닙니다. 이유는 PlaceQueryService 에 적어 두었습니다.
     *
     * 한 번에 100개까지 받습니다. 넘으면 400 VALIDATION_FAILED 입니다.
     * 식별자를 ids=a&ids=b 처럼 주소에 실어 오므로 하나에 41바이트가 들고,
     * Tomcat 이 요청 줄과 헤더를 합쳐 8KB 까지만 받아 180개 언저리가 이미 천장입니다.
     * 그 천장을 넘으면 컨트롤러에 닿기 전에 공통 응답 형태가 아닌 400 이 나가고
     * 이 서비스 로그에도 거의 남지 않습니다. 100 은 그 절반입니다.
     *
     * 즐겨찾기와 방문 기록은 페이징이 없어 101개부터 목록이 실패합니다.
     * 천장을 없애는 길은 부르는 쪽이 100개씩 나눠 부르는 것입니다.
     *
     * 상한은 @Size 로 겁니다.
     * 넘으면 스프링 MVC 가 HandlerMethodValidationException 을 던지고 common 0.0.14 가 400 으로 돌려줍니다.
     * 그 전 버전에서는 폴백이 잡아 500 이 났습니다.
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<PlaceSummaryOutput>>> getPlaces(
            @RequestParam("ids")
            @Size(max = 100, message = "한 번에 100개까지 조회할 수 있습니다.")
            List<UUID> ids) {

        return ResponseEntity.ok(CommonApiResponse.success(placeQueryService.getSummaries(ids)));
    }

    /**
     * 검색 서비스가 색인을 세울 때 부르는 조회입니다.
     *
     * 두 방식이 한 경로에 있습니다.
     *   ids 가 있으면   그 장소들을 돌려줍니다. 100개까지이며 place.updated 를 받고 다시 읽을 때 씁니다
     *   ids 가 없으면   id 순으로 after 다음 장소들을 size 개 돌려줍니다. 전량 재색인에 씁니다
     *                  after 가 없으면 처음부터이고 size 는 1 에서 500 사이로 맞춥니다
     * ids 와 after 를 함께 주면 400 VALIDATION_FAILED 입니다. 어느 방식인지 정할 수 없습니다.
     *
     * 응답은 목록 그대로입니다. 받은 수가 size 보다 적으면 끝입니다.
     * 폐업한 장소도 담습니다.
     *
     * 위의 /internal/places?ids= 와 모양을 나눴습니다. 이유는 PlaceIndexingOutput 에 적었습니다.
     * ids 의 상한 100 은 위 조회와 같은 이유(주소 길이 천장)입니다.
     * 이어받기는 주소에 식별자를 싣지 않아 그 천장과 무관합니다.
     */
    @GetMapping("/indexing")
    public ResponseEntity<CommonApiResponse<List<PlaceIndexingOutput>>> getIndexing(
            @RequestParam(value = "ids", required = false)
            @Size(max = 100, message = "한 번에 100개까지 조회할 수 있습니다.")
            List<UUID> ids,
            @RequestParam(value = "after", required = false) UUID after,
            @RequestParam(value = "size", defaultValue = "" + PlaceQueryService.MAX_INDEXING_SIZE) int size) {

        if (ids != null && after != null) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        List<PlaceIndexingOutput> result = ids != null
                ? placeQueryService.getIndexingByIds(ids)
                : placeQueryService.getIndexingAfter(after, size);
        return ResponseEntity.ok(CommonApiResponse.success(result));
    }

    /**
     * 수집 결과를 적재합니다.
     *
     * 적재하면서 정규화하고 같은 장소를 찾아 병합합니다.
     * 병합을 나중에 하지 않는 이유는 매칭 전 상태의 행을 만들지 않기 위해서입니다.
     * 그 행이 생기면 place_id 가 발급된 뒤 병합으로 죽는 행이 생기는데,
     * 증분 수집에서는 즐겨찾기와 방문 기록이 그 값을 물고 있어 되돌릴 수 없습니다.
     *
     * 응답에 건수를 담습니다.
     * 담지 않으면 보낸 수와 들어간 수가 다를 때 아무도 모릅니다.
     */
    @PostMapping("/bulk")
    public ResponseEntity<CommonApiResponse<BulkResult>> bulk(
            @Valid @RequestBody PlaceBulkRequest request) {

        BulkResult result = placeBulkService.ingest(request.toDrafts());

        log.info("적재했습니다: 요청={} 신규={} 병합={} 건너뜀={} 대기={} 대기실패={}",
                request.items().size(), result.created(), result.merged(),
                result.skipped(), result.pending(), result.pendingFailed());

        return ResponseEntity.ok(CommonApiResponse.success(result));
    }
}
