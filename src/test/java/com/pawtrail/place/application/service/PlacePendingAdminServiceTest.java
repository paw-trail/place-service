package com.pawtrail.place.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.place.application.dto.input.PlaceAdminUpdateInput;
import com.pawtrail.place.application.dto.output.PlacePendingOutput;
import com.pawtrail.place.domain.enums.PendingStatus;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.SourceType;
import com.pawtrail.place.domain.exception.PlaceErrorCode;
import com.pawtrail.place.domain.model.Place;
import com.pawtrail.place.domain.model.PlacePendingUpdate;
import com.pawtrail.place.domain.repository.PlacePendingUpdateRepository;
import com.pawtrail.place.domain.repository.PlaceRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 관리자가 반영 대기 값을 보고 처리하는 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 띄우지 않습니다.
 * 여기서 지키려는 것은 어느 값을 어떤 모양으로 넘기는가이며
 * 그 판단에 데이터베이스가 관여하지 않습니다.
 *
 * 특히 지키려는 것이 둘입니다.
 * 승인은 관리자 수정과 같은 경로를 타야 합니다.
 * 다른 경로로 쓰면 검증과 파생값 재계산이 갈려 같은 값인데 결과가 달라집니다.
 * 주소는 도로명과 지번이 한 덩어리라 한쪽만 넘기면 나머지가 지워집니다.
 */
@ExtendWith(MockitoExtension.class)
class PlacePendingAdminServiceTest {

    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PENDING_1 = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID PENDING_2 = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID MISSING = UUID.fromString("cccccccc-0000-7000-8000-000000000003");
    private static final String ADMIN = "admin-account-id";

    @Mock
    private PlacePendingUpdateRepository pendingUpdateRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceAdminService placeAdminService;

    @InjectMocks
    private PlacePendingAdminService placePendingAdminService;

    // ── 목록 ─────────────────────────────────────────────────

    @Test
    @DisplayName("장소 이름을 채워 돌려준다")
    void 목록에_장소_이름() {
        PlacePendingUpdate pending = pending(PENDING_1, "tel", "033-000-0000");
        when(pendingUpdateRepository.findPending(any(Pageable.class)))
                .thenReturn(page(pending));
        when(placeRepository.findAllById(List.of(PLACE_A))).thenReturn(List.of(place()));

        Page<PlacePendingOutput> result =
                placePendingAdminService.list(PageRequest.of(0, 20));

        assertThat(result).hasSize(1);
        PlacePendingOutput row = result.getContent().get(0);
        assertThat(row.pendingId()).isEqualTo(PENDING_1);
        assertThat(row.placeName()).isEqualTo("A 공원");
        assertThat(row.fieldName()).isEqualTo("tel");
        assertThat(row.newValue()).isEqualTo("033-000-0000");
    }

    @Test
    @DisplayName("대기 값이 없으면 장소를 조회하지 않는다")
    void 빈_목록() {
        when(pendingUpdateRepository.findPending(any(Pageable.class))).thenReturn(page());

        assertThat(placePendingAdminService.list(PageRequest.of(0, 20))).isEmpty();

        verify(placeRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("승인하기 전에 장소를 잠근다")
    void 승인은_장소를_잠근다() {
        PlacePendingUpdate pending = pending(PENDING_1, "tel", "033-000-0000");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(pending));
        when(placeRepository.findByIdForUpdate(PLACE_A)).thenReturn(Optional.of(place()));

        // 주소가 아닌 필드에도 걸어 둠
        // 짝이 되는 값을 읽는 사이에 관리자 수정이 커밋되면 옛 값을 덮어씀
        placePendingAdminService.approve(PENDING_1, ADMIN);

        verify(placeRepository).findByIdForUpdate(PLACE_A);
        verify(placeRepository, never()).findById(any());
    }

    // ── 승인 ─────────────────────────────────────────────────

    @Test
    @DisplayName("승인하면 관리자 수정 경로로 그 값을 넘긴다")
    void 승인은_수정_경로를_탄다() {
        PlacePendingUpdate pending = pending(PENDING_1, "tel", "033-000-0000");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(pending));
        when(placeRepository.findByIdForUpdate(PLACE_A)).thenReturn(Optional.of(place()));

        placePendingAdminService.approve(PENDING_1, ADMIN);

        PlaceAdminUpdateInput input = capturedInput();
        assertThat(input.telProvided()).isTrue();
        assertThat(input.tel()).isEqualTo("033-000-0000");
        assertThat(input.name()).isNull();
        assertThat(input.addressProvided()).isFalse();
    }

    @Test
    @DisplayName("승인하면 처리됨으로 표시한다")
    void 승인_표시() {
        PlacePendingUpdate pending = pending(PENDING_1, "homepage", "https://example.com");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(pending));
        when(placeRepository.findByIdForUpdate(PLACE_A)).thenReturn(Optional.of(place()));

        placePendingAdminService.approve(PENDING_1, ADMIN);

        assertThat(pending.getStatus()).isEqualTo(PendingStatus.APPROVED);
        assertThat(pending.getResolvedBy()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("이름을 승인하면 이름 칸만 세운다")
    void 이름_승인() {
        PlacePendingUpdate pending = pending(PENDING_1, "name", "새 이름");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(pending));
        when(placeRepository.findByIdForUpdate(PLACE_A)).thenReturn(Optional.of(place()));

        placePendingAdminService.approve(PENDING_1, ADMIN);

        PlaceAdminUpdateInput input = capturedInput();
        assertThat(input.name()).isEqualTo("새 이름");
        assertThat(input.telProvided()).isFalse();
    }

    @Test
    @DisplayName("도로명을 승인하면 짝이 되는 지번 대기 값도 함께 넘기고 함께 처리한다")
    void 주소를_묶어_승인() {
        PlacePendingUpdate road = pending(PENDING_1, "address_road", "부산광역시 해운대구 해운대해변로 264");
        PlacePendingUpdate jibun = pending(PENDING_2, "address_jibun", "부산광역시 해운대구 중동 1015");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(road));
        when(pendingUpdateRepository.findPendingByPlaceIdAndFieldName(PLACE_A, "address_jibun"))
                .thenReturn(Optional.of(jibun));
        when(placeRepository.findByIdForUpdate(PLACE_A)).thenReturn(Optional.of(place()));

        placePendingAdminService.approve(PENDING_1, ADMIN);

        PlaceAdminUpdateInput input = capturedInput();
        assertThat(input.addressProvided()).isTrue();
        assertThat(input.addressRoad()).isEqualTo("부산광역시 해운대구 해운대해변로 264");
        assertThat(input.addressJibun()).isEqualTo("부산광역시 해운대구 중동 1015");
        assertThat(jibun.getStatus()).isEqualTo(PendingStatus.APPROVED);
    }

    @Test
    @DisplayName("짝이 되는 대기 값이 없으면 장소의 지금 값을 쓴다")
    void 짝이_없으면_현재_값() {
        PlacePendingUpdate road = pending(PENDING_1, "address_road", "부산광역시 해운대구 해운대해변로 264");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(road));
        when(pendingUpdateRepository.findPendingByPlaceIdAndFieldName(PLACE_A, "address_jibun"))
                .thenReturn(Optional.empty());
        when(placeRepository.findByIdForUpdate(PLACE_A)).thenReturn(Optional.of(place()));

        // 짝이 없다는 것은 그 소스가 지번을 바꾸지 않았다는 뜻임
        placePendingAdminService.approve(PENDING_1, ADMIN);

        assertThat(capturedInput().addressJibun()).isEqualTo("서울특별시 송파구 잠실동 1");
    }

    @Test
    @DisplayName("지번을 승인해도 도로명이 함께 실린다")
    void 지번을_승인해도_덩어리() {
        PlacePendingUpdate jibun = pending(PENDING_2, "address_jibun", "부산광역시 해운대구 중동 1015");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_2)).thenReturn(Optional.of(jibun));
        when(pendingUpdateRepository.findPendingByPlaceIdAndFieldName(PLACE_A, "address_road"))
                .thenReturn(Optional.empty());
        when(placeRepository.findByIdForUpdate(PLACE_A)).thenReturn(Optional.of(place()));

        placePendingAdminService.approve(PENDING_2, ADMIN);

        PlaceAdminUpdateInput input = capturedInput();
        assertThat(input.addressRoad()).isEqualTo("서울특별시 송파구 도로 1");
        assertThat(input.addressJibun()).isEqualTo("부산광역시 해운대구 중동 1015");
    }

    @Test
    @DisplayName("없는 대기 값은 PENDING_NOT_FOUND 다")
    void 없는_대기_값() {
        when(pendingUpdateRepository.findByIdForUpdate(MISSING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placePendingAdminService.approve(MISSING, ADMIN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PENDING_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 처리한 값은 PENDING_ALREADY_RESOLVED 다")
    void 이미_처리된_값() {
        PlacePendingUpdate pending = pending(PENDING_1, "tel", "033-000-0000");
        pending.reject(ADMIN);
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(pending));

        // 엔티티도 막으나 거기는 IllegalStateException 이라 공통 폴백이 500 을 냄
        assertThatThrownBy(() -> placePendingAdminService.approve(PENDING_1, ADMIN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.PENDING_ALREADY_RESOLVED);

        verify(placeAdminService, never()).update(any(), any());
    }

    // ── 반려 ─────────────────────────────────────────────────

    @Test
    @DisplayName("반려하면 place 를 건드리지 않는다")
    void 반려는_반영하지_않는다() {
        PlacePendingUpdate pending = pending(PENDING_1, "tel", "033-000-0000");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(pending));

        placePendingAdminService.reject(PENDING_1, ADMIN);

        assertThat(pending.getStatus()).isEqualTo(PendingStatus.REJECTED);
        assertThat(pending.getResolvedBy()).isEqualTo(ADMIN);
        verify(placeAdminService, never()).update(any(), any());
    }

    @Test
    @DisplayName("주소를 반려해도 짝은 건드리지 않는다")
    void 반려는_필드_단위() {
        PlacePendingUpdate road = pending(PENDING_1, "address_road", "부산광역시 해운대구 해운대해변로 264");
        when(pendingUpdateRepository.findByIdForUpdate(PENDING_1)).thenReturn(Optional.of(road));

        // 도로명은 틀렸고 지번은 맞을 수 있어 필드마다 따로 판단함
        placePendingAdminService.reject(PENDING_1, ADMIN);

        verify(pendingUpdateRepository, never()).findPendingByPlaceIdAndFieldName(any(), any());
    }

    // ── 준비 ─────────────────────────────────────────────────

    private PlaceAdminUpdateInput capturedInput() {
        ArgumentCaptor<PlaceAdminUpdateInput> captor =
                ArgumentCaptor.forClass(PlaceAdminUpdateInput.class);
        verify(placeAdminService).update(eq(PLACE_A), captor.capture());
        return captor.getValue();
    }

    private static Page<PlacePendingUpdate> page(PlacePendingUpdate... rows) {
        return new PageImpl<>(List.of(rows), PageRequest.of(0, 20), rows.length);
    }

    private static PlacePendingUpdate pending(UUID id, String fieldName, String newValue) {
        PlacePendingUpdate pending = PlacePendingUpdate.detect(
                PLACE_A, fieldName, "옛 값", newValue, SourceType.CULTURE_CSV);
        setField(pending, "id", id);
        return pending;
    }

    /**
     * 주소가 둘 다 채워진 장소를 만듭니다.
     *
     * 짝이 없을 때 지금 값을 쓰는지 보려면 그 값이 있어야 합니다.
     */
    private static Place place() {
        Place place = Place.create("A 공원", PlaceType.PARK,
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"));
        setField(place, "id", PLACE_A);
        place.applyAddress("서울특별시 송파구 도로 1", "서울특별시 송파구 잠실동 1", "11", null);
        return place;
    }

    /**
     * 리플렉션으로 값을 넣습니다.
     *
     * 검사를 위해 엔티티에 setter 를 여는 것보다 낫습니다.
     * 그 setter 는 운영 코드에서 아무도 쓰지 않으면서 아무나 값을 바꿀 수 있게 만듭니다.
     * 같은 폴더의 다른 테스트와 같은 방식입니다.
     */
    private static void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(name + " 을 넣지 못했습니다", e);
            }
        }
        throw new IllegalStateException(name + " 필드를 찾지 못했습니다");
    }
}
