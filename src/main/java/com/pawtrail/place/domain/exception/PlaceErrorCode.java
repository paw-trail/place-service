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
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다.");

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
