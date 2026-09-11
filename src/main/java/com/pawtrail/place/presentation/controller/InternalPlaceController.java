package com.pawtrail.place.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.place.application.dto.output.BulkResult;
import com.pawtrail.place.application.service.PlaceIngestService;
import com.pawtrail.place.presentation.request.PlaceBulkRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private final PlaceIngestService placeIngestService;

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

        BulkResult result = placeIngestService.ingest(request.toDrafts());

        log.info("적재했습니다: 요청={} 신규={} 병합={} 건너뜀={} 대기={} 대기실패={}",
                request.items().size(), result.created(), result.merged(),
                result.skipped(), result.pending(), result.pendingFailed());

        return ResponseEntity.ok(CommonApiResponse.success(result));
    }
}
