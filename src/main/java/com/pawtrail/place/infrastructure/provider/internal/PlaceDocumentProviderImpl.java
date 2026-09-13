package com.pawtrail.place.infrastructure.provider.internal;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.place.domain.exception.PlaceErrorCode;
import com.pawtrail.place.domain.provider.PlaceDocumentProvider;
import com.pawtrail.place.domain.provider.dto.RawDocumentView;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 도메인이 선언한 약속을 수집 서비스 호출로 구현합니다.
 *
 * internal 아래에 두는 것은 우리가 만든 다른 서비스이기 때문입니다.
 * external 은 카카오처럼 바깥 시스템을 부르는 자리입니다.
 *
 * ⚠상대가 평소에 떠 있지 않습니다.
 * 수집 서비스는 받아 올 때만 켜는 배치라 유레카에 없는 상태가 기본입니다.
 * 그래서 이 호출이 실패하는 것은 이상한 일이 아니고, 부르는 쪽이 그것을 전제로 다룹니다.
 */
@Slf4j
@Component
public class PlaceDocumentProviderImpl implements PlaceDocumentProvider {

    private static final String BASE_URL = "lb://ingest-service";

    private final RestClient restClient;

    /**
     * 빌더를 주입받아 RestClient 를 만듭니다.
     *
     * RestClient.builder() 를 직접 부르지 않습니다.
     * 그러면 인증 헤더도 lb:// 해석도 시간 제한도 붙지 않습니다.
     * 공통 모듈이 그 셋을 미리 걸어 둔 빌더를 내어 줍니다.
     *
     * 이름을 반드시 적어야 합니다.
     * 같은 타입의 빈이 셋이고 그중 하나가 기본으로 지정되어 있습니다.
     * 빠뜨리면 아무것도 얹히지 않은 그 빌더가 조용히 주입되어
     * lb:// 를 풀지 못하고 기동이 아니라 호출하는 순간에 실패합니다.
     *
     * 롬복의 생성자 애노테이션을 쓰지 않는 것도 그 때문입니다.
     * 그것이 만드는 생성자에는 이름이 붙지 않습니다.
     */
    public PlaceDocumentProviderImpl(
            @Qualifier("internalRestClientBuilder") RestClient.Builder builder) {

        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    /**
     * 그 장소의 원본을 가져옵니다.
     *
     * 실패를 빈 목록으로 바꾸지 않고 예외를 던집니다.
     *
     * 다른 서비스 호출에서는 빈 값으로 돌려주고 화면을 띄우는 편이 나은 자리가 많습니다.
     * 즐겨찾기 목록에서 장소 이름을 못 불러도 목록 자체는 보여주는 것이 그렇습니다.
     * 여기는 반대입니다. 원문이 그 화면의 주인공이라 비어 있으면 볼 것이 없고,
     * 원본을 거치지 않는 소스로만 만들어진 장소는 실제로 빈 목록이 정상이라
     * 둘을 같은 모양으로 만들면 가를 수 없게 됩니다.
     *
     * 잡는 범위를 넓게 둡니다.
     * 연결 거부, 시간 초과, 서비스를 못 찾는 것, 응답 형태가 다른 것까지
     * 우리가 할 일이 같습니다. 지금은 못 가져온다고 알리는 것입니다.
     */
    @Override
    public List<RawDocumentView> findByPlaceId(UUID placeId) {
        try {
            CommonApiResponse<DocumentsResponse> response = restClient.get()
                    .uri("/internal/raw/{placeId}/documents", placeId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null
                    || response.getData().documents() == null) {

                log.warn("수집 서비스 응답이 비어 있습니다. placeId={}", placeId);
                throw new CustomException(PlaceErrorCode.PLACE_DOCUMENTS_UNAVAILABLE);
            }

            return response.getData().documents();

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // 로그를 warn 으로 둠
            //
            // 상대가 평소에 안 떠 있어 이 실패가 흔하며 그것이 잘못된 상태가 아님
            // error 로 두면 진짜 문제가 이 줄에 묻힘
            log.warn("원문을 가져오지 못했습니다. placeId={}, reason={}", placeId, e.getMessage());
            throw new CustomException(PlaceErrorCode.PLACE_DOCUMENTS_UNAVAILABLE);
        }
    }

    /**
     * 수집 서비스의 응답 봉투입니다.
     *
     * 그쪽이 목록을 한 겹 감싸 주므로 여기서도 그 모양으로 받습니다.
     */
    private record DocumentsResponse(List<RawDocumentView> documents) {
    }
}
