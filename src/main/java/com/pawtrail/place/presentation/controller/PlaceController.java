package com.pawtrail.place.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.place.application.dto.output.PlaceDetailOutput;
import com.pawtrail.place.application.service.PlaceQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브라우저가 게이트웨이를 거쳐 부르는 장소 API 입니다.
 *
 * 게이트웨이는 /api/v1/places/ 아래를 서비스 여섯으로 가릅니다.
 * 이 서비스가 받는 것은 {placeId} 와 {placeId}/documents 둘뿐이고,
 * /verdict · /reviews · /conflicts · /congestion 은 각각 다른 서비스가 받습니다.
 *
 * 로그인한 요청만 닿습니다.
 * 공통 보안 체인이 /internal 과 /actuator 를 뺀 나머지를 인증된 요청으로만 받습니다.
 */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    /**
     * 장소 상세를 돌려줍니다.
     *
     * 상세 화면은 이 응답과 판정 · 후기 · 집중률 · 원문 · 조건 충돌을 병렬로 불러 조립합니다.
     * 서버가 조립하지 않으므로 다른 서비스가 죽어도 이 응답은 그대로 나갑니다.
     *
     * placeId 가 UUID 형식이 아니면 공통 핸들러가 400 VALIDATION_FAILED 로 돌려줍니다.
     * 형식은 맞는데 장소가 없으면 404 PLACE_NOT_FOUND 입니다.
     */
    @GetMapping("/{placeId}")
    public ResponseEntity<CommonApiResponse<PlaceDetailOutput>> getPlace(@PathVariable UUID placeId) {
        return ResponseEntity.ok(CommonApiResponse.success(placeQueryService.getDetail(placeId)));
    }
}
