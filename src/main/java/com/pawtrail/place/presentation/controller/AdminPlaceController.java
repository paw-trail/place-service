package com.pawtrail.place.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.place.application.dto.output.PlaceDetailOutput;
import com.pawtrail.place.application.service.PlaceAdminService;
import com.pawtrail.place.presentation.request.PlaceAdminUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
