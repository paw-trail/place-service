package com.pawtrail.place.presentation.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawtrail.place.application.dto.input.PlaceAdminUpdateInput;
import com.pawtrail.place.domain.enums.PlaceStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자가 장소를 고칠 때 보내는 것입니다.
 *
 * 이 서비스에서 record 가 아닌 유일한 요청입니다.
 *
 * PATCH 는 보낸 것만 바꾸는 것이 계약이라
 * 키를 아예 안 보낸 것과 명시적으로 null 을 보낸 것을 갈라야 합니다.
 * record 로 받으면 둘 다 null 이 되어 구분할 수 없고,
 * 그러면 전화번호만 고치려고 보낸 요청이 소개문까지 지웁니다.
 *
 * 잭슨은 JSON 에 그 키가 있을 때만 세터를 부릅니다.
 * 그래서 세터 안에서 플래그를 세우면 별도 라이브러리 없이 세 상태가 갈립니다.
 * Optional 로 받는 방법도 있으나 잭슨이 없음과 명시적 null 을
 * 둘 다 Optional.empty() 로 만들 수 있어 확실하지 않습니다.
 * user 의 프로필 수정이 같은 방식을 씁니다.
 *
 * 세 상태가 이렇게 갈립니다.
 *   키가 없음          provided 가 거짓          그대로 둠
 *   "필드": null      provided 가 참, 값 null   지움. 이름과 상태는 400
 *   "필드": "값"       provided 가 참, 값 있음   바꿈
 *
 * 받는 것은 사람이 보고 맞다 틀리다를 판단할 수 있는 값뿐입니다.
 * 분류와 좌표는 받지 않습니다. 고치면 검색 카테고리와 지도와 병합이 함께 흔들리는데
 * 관리자 화면에 지도 편집기가 없어 좌표를 숫자로 받으면 틀려도 알아챌 수 없습니다.
 * 원천 분류와 저작권 구분과 데이터 기준일도 받지 않습니다.
 * 소스가 준 것을 그대로 담는 자리라 고치면 원문이 아니게 됩니다.
 */
@Getter
@NoArgsConstructor
public class PlaceAdminUpdateRequest {

    // 지점명을 포함하는 값이라 폭이 넓음
    // place.name 이 varchar(200) 이며 그것을 그대로 상한으로 씀
    @Size(max = 200, message = "이름은 200자를 넘을 수 없습니다.")
    private String name;

    private boolean nameProvided;

    @Size(max = 300, message = "도로명 주소는 300자를 넘을 수 없습니다.")
    private String addressRoad;

    @Size(max = 300, message = "지번 주소는 300자를 넘을 수 없습니다.")
    private String addressJibun;

    // 도로명과 지번을 한 덩어리로 봄
    //
    // 정규화 값과 시도 코드가 둘에서 함께 나오므로 따로 다루면
    // 한쪽만 바뀐 주소에서 정규화 값을 만들게 됨
    // 둘 중 하나만 보내도 주소를 고치는 것으로 취급함
    private boolean addressProvided;

    @Size(max = 30, message = "전화번호는 30자를 넘을 수 없습니다.")
    private String tel;

    private boolean telProvided;

    // 홈페이지와 예약처와 사진 주소는 컬럼이 text 라 길이를 걸지 않음
    // 소스가 앵커 태그를 통째로 주는 경우가 있어 상한을 잡을 근거가 없음
    private String homepage;

    private boolean homepageProvided;

    private String reservationUrl;

    private boolean reservationUrlProvided;

    private String imageUrl;

    private boolean imageUrlProvided;

    private String overview;

    private boolean overviewProvided;

    @Size(max = 600, message = "영업시간은 600자를 넘을 수 없습니다.")
    private String businessHours;

    private boolean businessHoursProvided;

    @Size(max = 200, message = "휴무일은 200자를 넘을 수 없습니다.")
    private String closedDays;

    private boolean closedDaysProvided;

    // ACTIVE · CLOSED · UNKNOWN
    //
    // 폐업 제보를 처리하는 자리가 이것임
    // 폐업해도 행을 지우지 않고 이 값으로 표시함
    private PlaceStatus status;

    private boolean statusProvided;

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    @JsonProperty("addressRoad")
    public void setAddressRoad(String addressRoad) {
        this.addressRoad = addressRoad;
        this.addressProvided = true;
    }

    @JsonProperty("addressJibun")
    public void setAddressJibun(String addressJibun) {
        this.addressJibun = addressJibun;
        this.addressProvided = true;
    }

    @JsonProperty("tel")
    public void setTel(String tel) {
        this.tel = tel;
        this.telProvided = true;
    }

    @JsonProperty("homepage")
    public void setHomepage(String homepage) {
        this.homepage = homepage;
        this.homepageProvided = true;
    }

    @JsonProperty("reservationUrl")
    public void setReservationUrl(String reservationUrl) {
        this.reservationUrl = reservationUrl;
        this.reservationUrlProvided = true;
    }

    @JsonProperty("imageUrl")
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        this.imageUrlProvided = true;
    }

    @JsonProperty("overview")
    public void setOverview(String overview) {
        this.overview = overview;
        this.overviewProvided = true;
    }

    @JsonProperty("businessHours")
    public void setBusinessHours(String businessHours) {
        this.businessHours = businessHours;
        this.businessHoursProvided = true;
    }

    @JsonProperty("closedDays")
    public void setClosedDays(String closedDays) {
        this.closedDays = closedDays;
        this.closedDaysProvided = true;
    }

    @JsonProperty("status")
    public void setStatus(PlaceStatus status) {
        this.status = status;
        this.statusProvided = true;
    }

    /**
     * 이름을 지우려는 요청을 막습니다.
     *
     * 컬럼이 NOT NULL 이고 화면에 이름을 비우는 동작이 없습니다.
     * 어떤 정상 경로로도 오지 않으므로 오면 프론트의 버그이거나 조작입니다.
     *
     * user 가 닉네임에 둔 것과 같은 기준입니다.
     */
    @AssertTrue(message = "이름은 지울 수 없습니다.")
    public boolean isNameNotCleared() {
        return !nameProvided || name != null;
    }

    /**
     * 상태를 지우려는 요청을 막습니다.
     *
     * 이름과 같은 이유입니다. 컬럼이 NOT NULL 이고 화면에 비우는 동작이 없습니다.
     * 폐업이 아닌 상태로 되돌리려면 ACTIVE 를 보내면 됩니다.
     */
    @AssertTrue(message = "영업 상태는 지울 수 없습니다.")
    public boolean isStatusNotCleared() {
        return !statusProvided || status != null;
    }

    /**
     * 주소를 지우려는 요청을 막습니다.
     *
     * 도로명과 지번 중 하나만 있어도 됩니다. 둘 다 비었을 때만 막습니다.
     *
     * 주소가 없어지면 정규화 주소도 없어져 그 장소가 주소 매칭에서 통째로 빠집니다.
     * 관리자 화면의 주소 칸은 고치는 자리이지 지우는 자리가 아닙니다.
     *
     * 엔티티도 같은 것을 막습니다. 거기는 마지막 방어선이라 IllegalArgumentException 입니다.
     * 사용자에게 보이는 실패는 여기서 400 으로 내보냅니다.
     * user 가 닉네임에 둔 것과 같은 구조입니다.
     */
    @AssertTrue(message = "주소는 비울 수 없습니다.")
    public boolean isAddressNotCleared() {
        if (!addressProvided) {
            return true;
        }
        return (addressRoad != null && !addressRoad.isBlank())
                || (addressJibun != null && !addressJibun.isBlank());
    }

    /**
     * 고칠 것을 하나도 안 보낸 요청을 막습니다.
     *
     * 빈 본문을 받아 주면 아무것도 안 바꾸면서 admin_locked 만 켜집니다.
     * 그 뒤로 수집이 그 장소를 영영 고치지 못하는데 관리자는 그런 줄을 모릅니다.
     * 잠금을 푸는 화면이 없어 되돌릴 수도 없습니다.
     */
    @AssertTrue(message = "고칠 값을 하나 이상 보내야 합니다.")
    public boolean isAnyFieldProvided() {
        return nameProvided || addressProvided || telProvided || homepageProvided
                || reservationUrlProvided || imageUrlProvided || overviewProvided
                || businessHoursProvided || closedDaysProvided || statusProvided;
    }

    /**
     * 서비스가 받는 형태로 바꿉니다.
     *
     * 이름과 상태는 플래그를 넘기지 않습니다.
     * 명시적 null 이 위 검증에서 막히므로 null 은 안 보냈다는 뜻 하나만 남습니다.
     *
     * 나머지는 플래그가 필요합니다. null 이 지운다는 뜻으로 살아 있기 때문입니다.
     */
    public PlaceAdminUpdateInput toInput() {
        return new PlaceAdminUpdateInput(
                name,
                addressProvided, addressRoad, addressJibun,
                telProvided, tel,
                homepageProvided, homepage,
                reservationUrlProvided, reservationUrl,
                imageUrlProvided, imageUrl,
                overviewProvided, overview,
                businessHoursProvided, businessHours,
                closedDaysProvided, closedDays,
                status);
    }
}
