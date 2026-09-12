package com.pawtrail.place.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.place.application.dto.output.PlaceDetailOutput;
import com.pawtrail.place.application.dto.output.PlacePendingOutput;
import com.pawtrail.place.application.service.PlaceAdminService;
import com.pawtrail.place.application.service.PlacePendingAdminService;
import com.pawtrail.place.presentation.request.PlaceAdminUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자가 장소를 직접 다루는 API 입니다.
 *
 * 사람이 브라우저로 부르므로 /api/v1/admin 아래에 둡니다.
 * 서비스끼리 부르는 것은 /internal 이며 게이트웨이가 라우팅하지 않습니다.
 *
 * 관리자 권한 확인은 두 겹입니다.
 * 게이트웨이가 먼저 막고 공통 보안 체인이 한 번 더 봅니다.
 * 한쪽만 살아 있어도 막히므로 이 컨트롤러는 권한을 다시 확인하지 않습니다.
 */
@RestController
@RequestMapping("/api/v1/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController {

    private final PlaceAdminService placeAdminService;
    private final PlacePendingAdminService placePendingAdminService;

    /**
     * 장소를 고칩니다.
     *
     * 보낸 것만 바꾸고 그 장소를 잠급니다.
     * 이 뒤로 수집 배치는 이 행을 고치지 않고 발견한 값을 대기 목록에 쌓습니다.
     *
     * 응답은 상세 조회와 같은 형태입니다.
     * 고친 값과 함께 다시 만들어진 파생값이 그대로 드러나므로
     * 관리자가 이름을 고쳤을 때 정규화 값이 어떻게 됐는지를 그 자리에서 볼 수 있습니다.
     */
    @PatchMapping("/{placeId}")
    public ResponseEntity<CommonApiResponse<PlaceDetailOutput>> update(
            @PathVariable UUID placeId,
            @Valid @RequestBody PlaceAdminUpdateRequest request) {

        return ResponseEntity.ok(
                CommonApiResponse.success(placeAdminService.update(placeId, request.toInput())));
    }

    /**
     * 잘못 묶인 소스를 떼어냅니다.
     *
     * 경로의 sourceId 는 소스 이름이 아니라 place_source_link 의 식별자입니다.
     * 같은 소스가 한 장소에 둘 붙어 있는 경우가 있어 소스 이름으로는 어느 것을 뗄지 가릴 수 없습니다.
     * 같은 소스 안의 중복도 병합하기로 한 결정의 파급입니다.
     *
     * 뗀 사람을 기록에 남깁니다.
     * 그 표가 BaseEntity 를 상속하지 않아 자동으로 채워지지 않으므로 여기서 넘깁니다.
     * 상속하지 않은 이유는 행을 지우는 것이 관리자의 분리 판단을 무르는 일이기 때문입니다.
     *
     * 본문이 없어 204 로 답합니다.
     * 떼어낸 뒤의 상태가 궁금하면 상세 조회를 다시 부르면 됩니다.
     */
    @DeleteMapping("/{placeId}/sources/{sourceLinkId}")
    public ResponseEntity<Void> detachSource(
            @PathVariable UUID placeId,
            @PathVariable UUID sourceLinkId,
            @CurrentUser CustomUserPrincipal principal) {

        placeAdminService.detachSource(placeId, sourceLinkId, principal.accountId().toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * 수집이 반영하지 못한 값을 보여줍니다.
     *
     * 처리하지 않은 것만 담습니다.
     * 승인하거나 반려한 것까지 보이면 목록이 계속 길어지고
     * 배지에 뜨는 숫자가 "할 일 개수" 라는 뜻을 잃습니다.
     * 비어 있는 것이 정상이며 손댈 것이 없다는 뜻입니다.
     *
     * 상태나 장소로 거르는 파라미터를 두지 않았습니다.
     * 처리 이력을 보는 화면이 명세에 없고, 필요해지면 그때 하나 더하면 됩니다.
     */
    @GetMapping("/pending")
    public ResponseEntity<CommonApiResponse<PageResponse<PlacePendingOutput>>> getPending(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<PlacePendingOutput> pending = placePendingAdminService.list(pageable);
        return ResponseEntity.ok(CommonApiResponse.success(PageResponse.from(pending)));
    }

    /**
     * 그 값을 place 에 반영합니다.
     *
     * 관리자 수정과 같은 경로를 탑니다.
     * 같은 값을 손으로 고치나 이 버튼으로 넣나 결과가 달라질 이유가 없습니다.
     *
     * 주소는 도로명과 지번을 묶어 함께 반영합니다.
     * 짝이 되는 대기 값이 있으면 그것도 함께 처리됩니다.
     *
     * 본문이 없어 204 로 답합니다.
     * 반영된 모습이 궁금하면 장소 상세를 부르면 됩니다.
     */
    @PostMapping("/pending/{pendingId}/approve")
    public ResponseEntity<Void> approvePending(
            @PathVariable UUID pendingId,
            @CurrentUser CustomUserPrincipal principal) {

        placePendingAdminService.approve(pendingId, principal.accountId().toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * 그 값을 반영하지 않습니다. place 는 그대로 둡니다.
     *
     * 반려한 값은 다음 수집에서 다시 쌓이지 않습니다.
     * 소스가 값을 고치지 않는 한 같은 차이가 수집마다 발견되는데,
     * 그때마다 올라오면 관리자가 같은 판단을 되풀이하게 됩니다.
     *
     * 되돌리는 길이 없습니다.
     * 잘못 반려하면 그 소스가 값을 바꿔 보낼 때까지 다시 올라오지 않습니다.
     */
    @PostMapping("/pending/{pendingId}/reject")
    public ResponseEntity<Void> rejectPending(
            @PathVariable UUID pendingId,
            @CurrentUser CustomUserPrincipal principal) {

        placePendingAdminService.reject(pendingId, principal.accountId().toString());
        return ResponseEntity.noContent().build();
    }
}
