package com.pawtrail.place.presentation.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.place.application.service.PlaceBulkService;
import com.pawtrail.place.application.service.PlaceQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 색인용 조회가 두 방식 가운데 어느 쪽으로 가는지를 검사합니다.
 *
 * 컨트롤러를 직접 부릅니다. 스프링 MVC 는 띄우지 않습니다.
 * 여기서 지키려는 것은 ids 와 after 의 조합에 따라 갈리는 길이고,
 * 요청 줄을 읽어 값으로 바꾸는 일은 스프링이 합니다.
 */
@ExtendWith(MockitoExtension.class)
class InternalPlaceControllerTest {

    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");

    @Mock
    private PlaceBulkService placeBulkService;

    @Mock
    private PlaceQueryService placeQueryService;

    @InjectMocks
    private InternalPlaceController controller;

    @Test
    @DisplayName("ids 가 있으면 그 장소들을 읽는다")
    void ids_가_있으면() {
        controller.getIndexing(List.of(PLACE_A), null, 500);

        verify(placeQueryService).getIndexingByIds(List.of(PLACE_A));
        verify(placeQueryService, never()).getIndexingAfter(any(), anyInt());
    }

    @Test
    @DisplayName("ids 가 없으면 after 다음부터 이어 읽는다")
    void ids_가_없으면() {
        controller.getIndexing(null, PLACE_A, 200);

        verify(placeQueryService).getIndexingAfter(PLACE_A, 200);
    }

    @Test
    @DisplayName("둘 다 없으면 처음부터 읽는다")
    void 처음부터() {
        controller.getIndexing(null, null, 500);

        verify(placeQueryService).getIndexingAfter(null, 500);
    }

    @Test
    @DisplayName("ids 와 after 를 함께 주면 VALIDATION_FAILED 이고 아무것도 읽지 않는다")
    void 함께_주면_거절() {
        // 어느 방식으로 읽을지 정할 수 없음
        assertThatThrownBy(() -> controller.getIndexing(List.of(PLACE_A), PLACE_A, 500))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(placeQueryService);
    }
}
