package com.pawtrail.place.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.place.domain.enums.CoordSource;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.TelSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 병합할 때 빈 칸만 채우는 규칙을 고정합니다.
 *
 * 소스가 채우는 칸이 배타적이라 이 동작이 필요합니다.
 * 공사 계열은 지번을 하나도 주지 않고 문화정보원은 이미지를 하나도 주지 않습니다.
 * 대표 값만 쓰면 병합 그룹 146 개 중
 * 119 개가 지번을 잃거나 115 개가 이미지를 잃습니다.
 */
class PlaceFillEmptyTest {

    private static Place blank(String name) {
        return Place.create(name, PlaceType.PARK,
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"));
    }

    @Test
    @DisplayName("비어 있던 칸을 채운다")
    void 빈_칸을_채운다() {
        // 공사 계열이 대표이고 문화정보원이 지번을 주는 상황임
        Place primary = blank("여의도한강공원");
        primary.applyAddress("서울특별시 영등포구 여의동로 330", null, "11", "영등포구");

        Place other = blank("여의도한강공원");
        other.applyAddress(null, "서울특별시 영등포구 여의도동 8", null, null);

        primary.fillEmptyFrom(other);

        assertThat(primary.getAddressJibun()).isEqualTo("서울특별시 영등포구 여의도동 8");
        assertThat(primary.getAddressRoad()).isEqualTo("서울특별시 영등포구 여의동로 330");
    }

    @Test
    @DisplayName("값이 있던 칸은 건드리지 않는다")
    void 있던_값은_그대로() {
        // 그것이 대표 소스가 이겼다는 뜻임
        // overview 는 공사가 실제 소개문을 주고 문화정보원은 "관광지" 를 줌
        Place primary = blank("일출랜드");
        primary.applyDescription("천연용암동굴 미천굴을 중심으로 사계절 꽃의 축제가 열리는 곳", null, null);

        Place other = blank("일출랜드");
        other.applyDescription("관광지", "09:00-18:00", "연중무휴");

        primary.fillEmptyFrom(other);

        assertThat(primary.getOverview()).startsWith("천연용암동굴");
        assertThat(primary.getBusinessHours()).isEqualTo("09:00-18:00");
        assertThat(primary.getClosedDays()).isEqualTo("연중무휴");
    }

    @Test
    @DisplayName("전화번호와 출처를 짝으로 옮긴다")
    void 전화와_출처가_짝() {
        // 번호만 채우고 출처를 안 채우면 어디서 온 값인지 알 수 없게 됨
        Place primary = blank("어떤장소");

        Place other = blank("어떤장소");
        other.applyContact("033-455-7072", TelSource.CULTURE_CSV, null, null, null, null);

        primary.fillEmptyFrom(other);

        assertThat(primary.getTel()).isEqualTo("033-455-7072");
        assertThat(primary.getTelSource()).isEqualTo(TelSource.CULTURE_CSV);
    }

    @Test
    @DisplayName("사진과 저작권 구분을 짝으로 옮긴다")
    void 사진과_저작권이_짝() {
        Place primary = blank("어떤장소");

        Place other = blank("어떤장소");
        other.applyContact(null, null, null, null,
                "https://tong.visitkorea.or.kr/img.jpg", "Type3");

        primary.fillEmptyFrom(other);

        assertThat(primary.getImageUrl()).isEqualTo("https://tong.visitkorea.or.kr/img.jpg");
        assertThat(primary.getCpyrhtDivCd()).isEqualTo("Type3");
    }

    @Test
    @DisplayName("기준일은 더 최근 것을 남긴다")
    void 기준일은_최근_것() {
        // 빈 칸 채우기와 다른 규칙임
        // 둘 다 값이 있을 때 옛것을 남길 이유가 없음
        // 문화정보원만 해도 2025-03-24 판과 2022-11-30 판이 섞여 있음
        Place primary = blank("어떤장소");
        primary.applyDataBaseDate(LocalDate.of(2022, 11, 30));

        Place other = blank("어떤장소");
        other.applyDataBaseDate(LocalDate.of(2025, 3, 24));

        primary.fillEmptyFrom(other);

        assertThat(primary.getDataBaseDate()).isEqualTo(LocalDate.of(2025, 3, 24));
    }

    @Test
    @DisplayName("더 옛날 기준일은 덮어쓰지 않는다")
    void 옛_기준일은_무시() {
        Place primary = blank("어떤장소");
        primary.applyDataBaseDate(LocalDate.of(2025, 3, 24));

        Place other = blank("어떤장소");
        other.applyDataBaseDate(LocalDate.of(2022, 11, 30));

        primary.fillEmptyFrom(other);

        assertThat(primary.getDataBaseDate()).isEqualTo(LocalDate.of(2025, 3, 24));
    }

    @Test
    @DisplayName("이름은 채우지 않는다")
    void 이름은_안_채움() {
        // 이름이 갈리면 같은 장소로 보지도 않았을 것임
        Place primary = blank("고석정 꽃밭");
        Place other = blank("고석정꽃밭");
        other.applyNormalized("고석정꽃밭", List.of("별칭"), "강원|철원군");

        primary.fillEmptyFrom(other);

        assertThat(primary.getName()).isEqualTo("고석정 꽃밭");
        // 별칭은 비어 있었으므로 채워짐
        assertThat(primary.getNameAlias()).containsExactly("별칭");
    }

    @Test
    @DisplayName("이쪽에 주소가 있으면 저쪽 정규화 값을 가져오지 않는다")
    void 정규화_값은_주소와_한_덩어리() {
        // 원본 주소와 정규화 값이 서로 다른 소스를 가리키면
        // 그 값이 다음 병합의 후보 조회 키라 틀린 키로 매칭하게 됨
        Place primary = blank("어떤장소");
        primary.applyAddress("서울특별시 종로구 계동길 37", null, null, null);

        Place other = blank("어떤장소");
        other.applyAddress("부산광역시 해운대구 해운대로 100", null, "26", "해운대구");
        other.applyNormalized("어떤장소", List.of(), "부산|해운대구해운대로100");

        primary.fillEmptyFrom(other);

        assertThat(primary.getAddressNormalized()).isNull();
        assertThat(primary.getSidoCode()).isNull();
        assertThat(primary.getAddressRoad()).isEqualTo("서울특별시 종로구 계동길 37");
    }

    @Test
    @DisplayName("이쪽에 주소가 없으면 정규화 값까지 한 덩어리로 가져온다")
    void 주소가_없으면_덩어리로() {
        Place primary = blank("어떤장소");

        Place other = blank("어떤장소");
        other.applyAddress("부산광역시 해운대구 해운대로 100", "부산광역시 해운대구 우동 1", "26", "해운대구");
        other.applyNormalized("어떤장소", List.of(), "부산|해운대구해운대로100");

        primary.fillEmptyFrom(other);

        assertThat(primary.getAddressRoad()).isEqualTo("부산광역시 해운대구 해운대로 100");
        assertThat(primary.getAddressNormalized()).isEqualTo("부산|해운대구해운대로100");
        assertThat(primary.getSidoCode()).isEqualTo("26");
        assertThat(primary.getSigunguName()).isEqualTo("해운대구");
    }

    @Test
    @DisplayName("좌표가 다르면 좌표 출처를 가져오지 않는다")
    void 좌표가_다르면_출처를_안_가져옴() {
        // 좌표는 그대로 두고 출처만 가져오면 이 행의 좌표가 어디서 왔는지를 거짓으로 기록함
        // PlaceMatcher 가 그 값으로 판정 임계값을 가르므로 다음 매칭 결과까지 바뀜
        Place primary = Place.create("어떤장소", PlaceType.PARK,
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"));

        Place other = Place.create("어떤장소", PlaceType.PARK,
                new BigDecimal("35.1000000"), new BigDecimal("129.0000000"));
        other.applyCoordinate(new BigDecimal("35.1000000"), new BigDecimal("129.0000000"),
                CoordSource.GEOCODED);

        primary.fillEmptyFrom(other);

        assertThat(primary.getCoordSource()).isNull();
    }

    @Test
    @DisplayName("null 을 넘겨도 안 깨진다")
    void null_이면_그대로() {
        Place primary = blank("어떤장소");
        primary.applyContact("02-1234-5678", TelSource.PET_TOUR, null, null, null, null);

        primary.fillEmptyFrom(null);

        assertThat(primary.getTel()).isEqualTo("02-1234-5678");
    }

    @Test
    @DisplayName("좌표 출처가 비어 있으면 채운다")
    void 좌표_출처를_채운다() {
        Place primary = blank("어떤장소");
        Place other = blank("어떤장소");
        other.applyCoordinate(new BigDecimal("37.5000000"), new BigDecimal("127.0000000"),
                CoordSource.ORIGINAL);

        primary.fillEmptyFrom(other);

        assertThat(primary.getCoordSource()).isEqualTo(CoordSource.ORIGINAL);
    }
}
