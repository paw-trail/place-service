package com.pawtrail.place.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.place.domain.enums.CoordSource;
import com.pawtrail.place.domain.enums.PlaceStatus;
import com.pawtrail.place.domain.enums.PlaceType;
import com.pawtrail.place.domain.enums.TelSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 장소 마스터입니다. 소스 넷에서 모은 것을 병합해 장소당 한 행으로 둡니다.
 *
 * 반려동물 동반 조건은 한 필드도 없습니다.
 * 그것은 policy_db 의 소유이고 place 는 "장소가 무엇인가" 에만 답합니다.
 *
 * 이 엔티티의 id 는 한 번 밖으로 나가면 되돌릴 수 없습니다.
 * 즐겨찾기 · 방문 기록 · 일정 · 후기 · 제보가 전부 이 값을 물고 갑니다.
 * 그래서 병합 판정을 적재 시점에 끝내고 매칭 전 상태의 행을 만들지 않습니다.
 *
 * 값을 바꾸는 방법을 메서드로만 열어 둡니다.
 * 특히 admin_locked 은 관리자 수정에서만 켜져야 하므로 세터를 열지 않습니다.
 */
@Entity
@Table(name = "place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

    // 좌표계 식별자임, 4326 은 WGS84 위경도임
    // geom 을 만들 때마다 넣어 주어야 PostGIS 가 미터 단위 거리를 계산할 수 있음
    private static final int SRID_WGS84 = 4326;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // 사용자에게 보이는 이름임, 대표 소스의 값임
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // 매칭용으로 정규화한 이름임
    // 지점명은 보존함, 강남점과 홍대점은 다른 장소이기 때문임
    @Column(name = "name_normalized", length = 200)
    private String nameNormalized;

    // 괄호 별칭임, 송파나루공원(석촌호수) 에서 [석촌호수] 를 뽑아 둠
    //
    // 이 프로젝트의 첫 배열 컬럼임
    // 하이버네이트 6.1 부터 기본 컬렉션이 SqlTypes.ARRAY 로 매핑되므로
    // 외부 라이브러리 없이 text[] 에 그대로 붙음
    //
    // 파생 쿼리로는 배열을 검색할 수 없음
    // 매칭 조회는 ST_DWithin 때문에 어차피 네이티브라 거기서 배열 연산자를 함께 씀
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "name_alias", columnDefinition = "text[]")
    private List<String> nameAlias;

    @Column(name = "address_road", length = 300)
    private String addressRoad;

    // 지번 주소임, 문화정보원은 100% 채우고 공사 계열은 0% 임
    // 도로명이 없는 행의 주소 폴백으로 씀
    @Column(name = "address_jibun", length = 300)
    private String addressJibun;

    // 매칭 1순위 키임
    // 이 값이 틀리면 병합이 통째로 틀림
    // 시도명 표준화 하나만 빠져도 실측에서 병합 쌍 27 개가 누락됐음
    @Column(name = "address_normalized", length = 300)
    private String addressNormalized;

    // 법정동 코드의 시도 부분임, 공사 응답의 lDongRegnCd 에서 뽑음
    // 관광공사의 areaCode 가 아님, 둘은 값 체계가 다름
    //
    // 법정동 코드는 시도 2 + 시군구 3 + 읍면동 3 + 리 2 구조라
    // 길이와 무관하게 앞 두 자리가 시도를 유일하게 결정함
    // 소스가 5 자리를 주는 행이 실제로 있음(세종특별자치시)
    @Column(name = "sido_code", length = 2)
    private String sidoCode;

    // 법정동 코드의 시군구 부분임, 공사 응답의 lDongSignguCd 를 그대로 담음
    // 혼잡도 API 의 signguCd 와 같은 체계인지는 아직 확인하지 않았음
    @Column(name = "sigungu_code", length = 5)
    private String sigunguCode;

    // 공사 응답에서 mapY 가 위도이고 mapX 가 경도임, 순서가 반대임
    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lon", nullable = false, precision = 10, scale = 7)
    private BigDecimal lon;

    @Enumerated(EnumType.STRING)
    @Column(name = "coord_source", length = 10)
    private CoordSource coordSource;

    // 병합의 좌표 근접 매칭에 씀
    //
    // lat 과 lon 에서 만들며 직접 넣지 않음
    // 아래 syncGeom 이 저장과 수정 직전에 채워 두 값이 어긋나지 않게 함
    //
    // 컬럼이 geography 라 ST_DWithin 이 미터 단위 거리를 정확히 계산해 줌
    // lat 과 lon 으로 사각형을 만들려면 위도 1 도가 몇 미터인지를 직접 계산해야 하는데
    // 그 값이 위도마다 다름
    @Column(name = "geom", columnDefinition = "geography(Point,4326)")
    private Point geom;

    // 공사 원천 분류임, 가공하지 않고 그대로 담음
    // place_type 매핑 규칙을 나중에 바꿔도 다시 뽑을 수 있게 남겨 둠
    //
    // 폭이 100 인 이유는 고캠핑 induty 에 부대시설 목록이 통째로 들어간 행이 23 건 있어서임
    // 최장 79 자이며 소스 쪽 입력 오류라 우리가 고칠 수 없음
    // 잘라 담지 않는 것은 이 컬럼이 원문을 그대로 담는 자리이기 때문임
    @Column(name = "lcls1", length = 100)
    private String lcls1;

    @Column(name = "lcls2", length = 30)
    private String lcls2;

    @Column(name = "lcls3", length = 30)
    private String lcls3;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 12)
    private PlaceType placeType;

    // 공사 목록과 공통 응답은 전 계열 0% 이고 상세 응답의 infocenter 에서만 나옴
    @Column(name = "tel", length = 30)
    private String tel;

    @Enumerated(EnumType.STRING)
    @Column(name = "tel_source", length = 16)
    private TelSource telSource;

    @Column(name = "homepage", columnDefinition = "text")
    private String homepage;

    @Column(name = "reservation_url", columnDefinition = "text")
    private String reservationUrl;

    // 대표 사진임, 공사 firstimage 에서 옴
    // 같은 목록 안에서도 행마다 http 와 https 가 섞여 오므로 적재할 때 https 로 맞춤
    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    // 공사 응답의 cpyrhtDivCd 임, Type1 과 Type3 이 행마다 갈림
    // 지금 읽는 화면은 없으나 나중에 넣으려면 전량 재적재가 따라붙어 미리 담아 둠
    @Column(name = "cpyrht_div_cd", length = 10)
    private String cpyrhtDivCd;

    @Column(name = "overview", columnDefinition = "text")
    private String overview;

    @Column(name = "business_hours", length = 200)
    private String businessHours;

    @Column(name = "closed_days", length = 100)
    private String closedDays;

    // 폐업해도 행을 지우지 않고 이 값으로 표시함
    // 지우면 즐겨찾기 · 방문 기록 · 후기의 참조가 끊김
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PlaceStatus status;

    // 편의점이나 마트처럼 동선 중에 들르는 보급 지점인지임
    // 여정 지도가 place_type 과 이 값으로 마커 색을 가름
    @Column(name = "supply_point", nullable = false)
    private boolean supplyPoint;

    // 참이면 수집 배치가 이 행의 수정을 건너뛰고
    // 발견한 값을 place_pending_update 에 쌓음
    @Column(name = "admin_locked", nullable = false)
    private boolean adminLocked;

    // 소스가 준 데이터 기준일임
    // 소스별 고정값이 아니라 행마다 다름
    // 문화정보원만 해도 2025-03-24 판과 2022-11-30 판이 섞여 있음
    @Column(name = "data_base_date")
    private LocalDate dataBaseDate;

    private Place(String name, PlaceType placeType, BigDecimal lat, BigDecimal lon) {
        this.name = name;
        this.placeType = placeType;
        this.lat = lat;
        this.lon = lon;
        this.status = PlaceStatus.ACTIVE;
        this.supplyPoint = false;
        this.adminLocked = false;
    }

    /**
     * 수집이 새 장소를 발견했을 때 씁니다.
     *
     * 필수 넷만 받고 나머지는 적재 단계에서 채웁니다.
     * 생성자에 스물몇 개를 늘어놓으면 순서를 바꿔 넣어도 컴파일이 통과합니다.
     */
    public static Place create(String name, PlaceType placeType, BigDecimal lat, BigDecimal lon) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 은 필수입니다.");
        }
        if (placeType == null) {
            throw new IllegalArgumentException("placeType 은 필수입니다.");
        }
        if (lat == null || lon == null) {
            throw new IllegalArgumentException("좌표는 필수입니다. 지오코딩까지 실패했다면 적재하지 않습니다.");
        }
        return new Place(name, placeType, lat, lon);
    }

    /**
     * 매칭에 쓰는 정규화 값을 채웁니다.
     *
     * 정규화 규칙이 바뀌면 다시 부를 수 있도록 따로 열어 둡니다.
     */
    public void applyNormalized(String nameNormalized, List<String> nameAlias,
                                String addressNormalized) {
        this.nameNormalized = nameNormalized;
        this.nameAlias = nameAlias;
        this.addressNormalized = addressNormalized;
    }

    /**
     * 주소와 행정 코드를 채웁니다.
     */
    public void applyAddress(String addressRoad, String addressJibun,
                             String sidoCode, String sigunguCode) {
        this.addressRoad = addressRoad;
        this.addressJibun = addressJibun;
        this.sidoCode = sidoCode;
        this.sigunguCode = sigunguCode;
    }

    /**
     * 좌표를 바꿉니다. geom 은 syncGeom 이 저장 직전에 다시 만듭니다.
     */
    public void applyCoordinate(BigDecimal lat, BigDecimal lon, CoordSource coordSource) {
        if (lat == null || lon == null) {
            throw new IllegalArgumentException("좌표는 비울 수 없습니다.");
        }
        this.lat = lat;
        this.lon = lon;
        this.coordSource = coordSource;
    }

    /**
     * 분류를 채웁니다. 원천 분류는 가공하지 않고 그대로 담습니다.
     */
    public void applyClassification(PlaceType placeType, String lcls1, String lcls2, String lcls3) {
        if (placeType == null) {
            throw new IllegalArgumentException("placeType 은 비울 수 없습니다.");
        }
        this.placeType = placeType;
        this.lcls1 = lcls1;
        this.lcls2 = lcls2;
        this.lcls3 = lcls3;
    }

    /**
     * 연락처와 안내 정보를 채웁니다.
     */
    public void applyContact(String tel, TelSource telSource, String homepage,
                             String reservationUrl, String imageUrl, String cpyrhtDivCd) {
        this.tel = tel;
        this.telSource = telSource;
        this.homepage = homepage;
        this.reservationUrl = reservationUrl;
        this.imageUrl = imageUrl;
        this.cpyrhtDivCd = cpyrhtDivCd;
    }

    /**
     * 소개와 운영 정보를 채웁니다.
     */
    public void applyDescription(String overview, String businessHours, String closedDays) {
        this.overview = overview;
        this.businessHours = businessHours;
        this.closedDays = closedDays;
    }

    public void applyDataBaseDate(LocalDate dataBaseDate) {
        this.dataBaseDate = dataBaseDate;
    }

    public void changeStatus(PlaceStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status 는 비울 수 없습니다.");
        }
        this.status = status;
    }

    public void changeSupplyPoint(boolean supplyPoint) {
        this.supplyPoint = supplyPoint;
    }

    /**
     * 비어 있는 칸만 다른 소스의 값으로 채웁니다.
     *
     * 이미 값이 있는 칸은 건드리지 않습니다.
     * 그것이 대표 소스가 이겼다는 뜻입니다.
     *
     * 소스가 채우는 칸이 배타적이라 이 메서드가 필요합니다.
     * 공사 계열은 지번을 하나도 주지 않고 문화정보원은 이미지를 하나도 주지 않습니다.
     * 대표 값만 쓰면 병합 그룹 146 개 중 119 개가 지번을 잃거나 115 개가 이미지를 잃습니다.
     * 빈 칸을 채우면 대표를 누구로 하든 채움률이 82.4% 로 같아집니다.
     *
     * 채우지 않는 것이 넷입니다.
     *   name 과 name_normalized   이름이 갈리면 같은 장소로 보지도 않았을 것입니다
     *   lat 과 lon               NOT NULL 이라 빈 적이 없습니다
     *   place_type 과 status     NOT NULL 이며 판정은 대표 소스를 따릅니다
     *   admin_locked 와 supply_point   소스가 주는 값이 아닙니다
     *
     * geom 은 여기서 만들지 않습니다.
     * 좌표를 안 바꾸므로 그대로 두면 되고, 바꾼다면 syncGeom 이 저장 직전에 처리합니다.
     */
    public void fillEmptyFrom(Place other) {
        if (other == null) {
            return;
        }
        if (isBlank(nameAlias) && !isBlank(other.nameAlias)) {
            this.nameAlias = other.nameAlias;
        }
        if (isBlank(addressRoad)) {
            this.addressRoad = other.addressRoad;
        }
        if (isBlank(addressJibun)) {
            this.addressJibun = other.addressJibun;
        }
        // 주소 정규화 값은 도로명이나 지번에서 나온 것이라 함께 옮깁니다
        // 하나만 채우면 원본 주소와 정규화 값이 서로 다른 소스를 가리키게 됩니다
        if (isBlank(addressNormalized)) {
            this.addressNormalized = other.addressNormalized;
        }
        if (isBlank(sidoCode)) {
            this.sidoCode = other.sidoCode;
        }
        if (isBlank(sigunguCode)) {
            this.sigunguCode = other.sigunguCode;
        }
        if (isBlank(lcls1)) {
            this.lcls1 = other.lcls1;
        }
        if (isBlank(lcls2)) {
            this.lcls2 = other.lcls2;
        }
        if (isBlank(lcls3)) {
            this.lcls3 = other.lcls3;
        }
        // 전화번호와 그 출처는 짝이라 함께 옮깁니다
        // 번호만 채우고 출처를 안 채우면 어디서 온 값인지 알 수 없게 됩니다
        if (isBlank(tel) && !isBlank(other.tel)) {
            this.tel = other.tel;
            this.telSource = other.telSource;
        }
        if (isBlank(homepage)) {
            this.homepage = other.homepage;
        }
        if (isBlank(reservationUrl)) {
            this.reservationUrl = other.reservationUrl;
        }
        // 사진과 저작권 구분도 짝입니다
        if (isBlank(imageUrl) && !isBlank(other.imageUrl)) {
            this.imageUrl = other.imageUrl;
            this.cpyrhtDivCd = other.cpyrhtDivCd;
        }
        if (isBlank(overview)) {
            this.overview = other.overview;
        }
        if (isBlank(businessHours)) {
            this.businessHours = other.businessHours;
        }
        if (isBlank(closedDays)) {
            this.closedDays = other.closedDays;
        }
        if (coordSource == null) {
            this.coordSource = other.coordSource;
        }
        // 기준일은 더 최근 것을 남깁니다
        // 빈 칸 채우기와 다른 규칙인 이유는 둘 다 값이 있을 때 옛것을 남길 이유가 없기 때문입니다
        if (other.dataBaseDate != null
                && (dataBaseDate == null || other.dataBaseDate.isAfter(dataBaseDate))) {
            this.dataBaseDate = other.dataBaseDate;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isBlank(List<String> value) {
        return value == null || value.isEmpty();
    }

    /**
     * 관리자가 이 장소를 직접 고쳤음을 표시합니다.
     *
     * 이 뒤로 수집 배치는 이 행을 고치지 않고
     * 발견한 값을 place_pending_update 에 쌓습니다.
     *
     * 되돌리는 메서드를 두지 않았습니다.
     * 잠금을 푸는 화면이 명세에 없어 지금은 쓸 자리가 없습니다.
     */
    public void lockByAdmin() {
        this.adminLocked = true;
    }

    /**
     * lat 과 lon 에서 geom 을 다시 만듭니다.
     *
     * 저장과 수정 직전에 하이버네이트가 부르므로 부르는 쪽이 신경 쓸 것이 없습니다.
     * 좌표를 고쳤는데 geom 을 안 고치면 그 장소가 병합에서 옛 자리로 판정되는데,
     * 오류가 나지 않아 알아채기 어렵습니다.
     *
     * 순서가 위도와 경도가 아니라 경도와 위도임에 주의해야 합니다.
     * JTS 의 Coordinate 는 x 가 먼저이고 x 는 경도입니다.
     */
    @PrePersist
    @PreUpdate
    private void syncGeom() {
        if (lat == null || lon == null) {
            this.geom = null;
            return;
        }
        Point point = GEOMETRY_FACTORY.createPoint(
                new Coordinate(lon.doubleValue(), lat.doubleValue()));
        point.setSRID(SRID_WGS84);
        this.geom = point;
    }
}
