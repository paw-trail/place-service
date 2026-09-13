package com.pawtrail.place.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.place.application.dto.output.PlaceDetailOutput;
import com.pawtrail.place.application.dto.output.PlaceDocumentsOutput;
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

    /**
     * 그 장소가 어느 원본에서 왔는지를 돌려줍니다.
     *
     * 상세 화면의 「근거 원문 전체 보기」가 씁니다.
     * 우리가 여러 소스에서 값을 골라 하나로 합쳐 보여주므로
     * 어느 소스가 무엇이라고 했는지를 그대로 볼 수 있어야 사용자가 판단할 수 있습니다.
     * 실제로 같은 장소를 두고 한 소스는 일부구역 동반가능이라 하고
     * 다른 소스는 동반 불가능이라 하는 곳이 있습니다.
     *
     * 원본은 이 서비스가 가지고 있지 않습니다. 받아 온 쪽에 물어 옵니다.
     *
     * 장소가 없으면 404 이고, 원본을 가진 쪽을 못 부르면 503 입니다.
     * 빈 목록으로 돌려주지 않습니다.
     * 원본을 거치지 않는 소스로만 만들어진 장소는 실제로 빈 목록이 정상이라
     * 둘을 같은 모양으로 만들면 가를 수 없게 됩니다.
     */
    @GetMapping("/{placeId}/documents")
    public ResponseEntity<CommonApiResponse<PlaceDocumentsOutput>> getDocuments(
            @PathVariable UUID placeId) {

        return ResponseEntity.ok(CommonApiResponse.success(placeQueryService.getDocuments(placeId)));
    }
}
