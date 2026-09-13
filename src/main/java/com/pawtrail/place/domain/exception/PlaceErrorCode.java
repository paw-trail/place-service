package com.pawtrail.place.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 이 서비스의 도메인 에러 코드입니다.
 *
 * 공통 코드는 CommonErrorCode 에 있고 도메인 개념은 여기에 둡니다.
 * 공통에 두면 코드 하나를 더할 때마다 공통 모듈 재배포와 전 서비스 버전업이 필요해집니다.
 *
 * getCode 는 반드시 name 을 그대로 반환합니다.
 * 상수 이름이 곧 응답의 code 값이자 API 계약인데, 규칙을 어겨도 컴파일러가 잡지 못합니다.
 *
 * 메시지는 고정 문자열입니다. 동적인 값이 필요하면 응답 data 에 담습니다.
 *
 * 이 파일은 장소 조회에서 처음 생겼습니다.
 * 적재는 한 건이 잘못돼도 그 건만 건너뛰고 건수로 알리므로
 * 이름을 붙여 돌려줄 실패가 그전까지 없었습니다.
 *
 * 공통 코드를 쓸지 여기에 둘지는 메시지가 상황을 맞게 말하는가로 가릅니다.
 * user 가 방문 기록에서 세운 기준입니다.
 */
public enum PlaceErrorCode implements ErrorCode {

    // 그 장소가 없음
    //
    // * CommonErrorCode.RESOURCE_NOT_FOUND 를 쓰지 않는 이유
    //   그 코드의 메시지가 "요청하신 경로를 찾을 수 없습니다" 임
    //   없는 URL 을 불렀을 때 쓰려고 만든 것이라 여기서는 뜻이 어긋남
    //   주소는 맞게 불렀고 그 placeId 의 장소가 없는 것임
    //
    // * 폐업한 장소는 여기로 오지 않음
    //   폐업은 행을 지우지 않고 status 를 CLOSED 로 바꿔 표시하므로 200 으로 나감
    //
    // * 이름과 메시지는 common 의 ErrorCode 설명이 예로 든 것과 같음
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),

    // 그 장소에 그 소스가 붙어 있지 않음
    //
    // DELETE /admin/places/{id}/sources/{sourceId} 가 냄
    // 연결이 다른 장소에 붙어 있거나 이미 떼어진 경우임
    //
    // * PLACE_NOT_FOUND 와 가르는 이유
    //   장소는 있는데 그 소스가 없는 것이라 관리자가 봐야 할 것이 다름
    //   앞의 것은 주소를 잘못 부른 것이고 이것은 이미 처리됐거나 목록이 낡은 것임
    PLACE_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "그 장소에 묶인 소스가 아닙니다."),

    // 소스가 하나뿐이라 뗄 수 없음
    //
    // 이 표는 "지금 이 장소가 어느 소스로 이뤄져 있나" 를 담으므로
    // 0 개가 되면 그 답이 사라지고 장소의 존재 근거가 없어짐
    // 허용하면 원문 보기도 비는 유령 장소가 남는데
    // 즐겨찾기와 후기가 물고 있으면 지울 수도 없음
    //
    // * 장소를 없애는 것은 분리가 아니라 별도 기능이며 명세에 그 API 가 없음
    PLACE_LAST_SOURCE(HttpStatus.CONFLICT, "마지막 소스는 뗄 수 없습니다."),

    // 관리자가 보낸 주소를 정규화하지 못함
    //
    // AddressNormalizer 가 시도를 찾지 못하면 답하지 못함
    // "남정면 양성리" 처럼 시도를 빼고 적으면 그렇게 됨
    //
    // * 그대로 받으면 address_normalized 가 비고 그 장소가 주소 매칭에서 통째로 빠짐
    //   오류가 나지 않아 알아챌 수 없는 자리라 받는 쪽에서 막음
    //
    // * 적재는 같은 경우를 그대로 담음.  적재본에 정규화하지 못한 행이 다섯 있음
    //   기준이 갈리는 이유는 적재가 사람 없이 도는 배치이기 때문임
    //   여기는 관리자가 화면 앞에 있어 그 자리에서 되돌려 줄 수 있음
    PLACE_ADDRESS_INVALID(HttpStatus.BAD_REQUEST, "주소를 정규화할 수 없습니다. 시도부터 적어 주세요."),

    // 그 반영 대기 값이 없음
    //
    // POST /admin/places/pending/{id}/approve 와 /reject 가 냄
    // 목록이 낡아 이미 사라진 것을 누른 경우임
    PENDING_NOT_FOUND(HttpStatus.NOT_FOUND, "반영 대기 값을 찾을 수 없습니다."),

    // 이미 승인하거나 반려한 값임
    //
    // 엔티티도 같은 것을 막으나 거기는 마지막 방어선이라 IllegalStateException 임
    // 그대로 두면 공통 폴백이 잡아 500 이 나가므로 서비스가 먼저 막음
    //
    // * 400 이 아니라 409 인 이유
    //   요청 형식이 틀린 것이 아니라 지금 상태에서 할 수 없는 일임
    //   마지막 소스를 뗄 수 없는 것과 같은 부류임
    PENDING_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 처리된 반영 대기 값입니다."),

    // 관리자가 누른 재발행이 실패함
    //
    // 성공으로 응답하면 안 되는 자리임
    // 관리자는 보냈다고 알고 넘어가는데 이벤트는 여전히 안 나가며,
    // 그 상태가 바로 이 기능이 막으려던 것임
    //
    // 500 인 것은 사용자가 고칠 수 있는 것이 없기 때문임
    // 카프카가 죽어 있거나 이벤트 자체에 문제가 있는 경우라 우리가 봐야 함
    //
    // auth 의 같은 이름 코드와 값도 문구도 같음
    // 다섯 서비스의 아웃박스 화면이 한곳에 모이므로 응답이 서로 달라질 이유가 없음
    OUTBOX_REPUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이벤트 재발행에 실패했습니다."),

    // 원문을 가진 서비스를 부르지 못함
    //
    // * 빈 목록으로 돌려주지 않는 이유
    //   원문이 정말 없는 것과 지금 못 가져오는 것은 다른 상태임
    //   섞으면 사용자가 "이 장소는 근거가 없구나" 로 잘못 읽는데
    //   그것은 이 화면이 있는 이유를 정면으로 훼손함
    //   행안부 동물병원만으로 만들어진 장소는 원본을 안 거쳐 실제로 원문이 없음
    //
    // * 화면이 통째로 죽지는 않음
    //   상세 화면이 판정·후기·집중률·원문을 병렬로 불러 조립하므로
    //   이 카드만 오류가 되고 나머지는 그대로 뜸
    //
    // * 500 이 아니라 503 인 이유
    //   우리가 고칠 것이 아니라 상대가 지금 없는 것임
    //   수집 서비스는 상시 기동이 아니라 평소에 안 떠 있는 것이 정상임
    PLACE_DOCUMENTS_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, "원문을 지금 가져올 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    PlaceErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
