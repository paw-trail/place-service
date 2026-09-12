package com.pawtrail.place.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 대기 행을 만들지 못했을 때 그것이 중복인지 실패인지 가르는 규칙을 검사합니다.
 *
 * 이 판별이 틀리면 두 가지로 잘못됩니다.
 * 중복을 실패로 세면 관리자가 고칠 것이 있는 줄로 읽고,
 * 폭 초과를 중복으로 삼키면 값이 통째로 사라졌는데 아무도 모릅니다.
 *
 * 예외를 실제로 만들어 그대로 넘깁니다.
 * 하이버네이트가 제약 이름을 담아 주고 스프링이 그것을 감싸 던지는 형태를 그대로 재현합니다.
 *
 * 동시에 같은 값을 저장하는 상황은 검사하지 않습니다.
 * 스레드 타이밍에 기대는 검사는 간헐적으로 실패해 통과를 가장하게 됩니다.
 * 제약이 실제로 막는 것은 데이터베이스에 같은 값을 직접 넣어 확인했습니다.
 */
class PlaceIngestServiceTest {

    private static final String PENDING_UNIQUE = "uq_place_pending_unresolved";

    @Test
    @DisplayName("처리 전 대기 값의 중복 제약이면 중복이다")
    void 중복_제약() {
        DataIntegrityViolationException e = wrapped(PENDING_UNIQUE);

        assertThat(PlaceIngestService.isDuplicate(e)).isTrue();
    }

    @Test
    @DisplayName("제약 이름의 대소문자는 가리지 않는다")
    void 대소문자() {
        // 데이터베이스가 이름을 어떻게 돌려주는지에 기대지 않음
        DataIntegrityViolationException e = wrapped(PENDING_UNIQUE.toUpperCase());

        assertThat(PlaceIngestService.isDuplicate(e)).isTrue();
    }

    @Test
    @DisplayName("다른 제약이면 중복이 아니다")
    void 다른_제약() {
        DataIntegrityViolationException e = wrapped("uq_place_source");

        assertThat(PlaceIngestService.isDuplicate(e)).isFalse();
    }

    @Test
    @DisplayName("제약 위반이 아닌 원인이면 중복이 아니다")
    void 다른_원인() {
        // 컬럼 폭을 넘긴 경우가 이쪽임
        // 값이 통째로 안 들어갔으므로 관리자가 알아야 하는 실패임
        DataIntegrityViolationException e =
                new DataIntegrityViolationException("값이 너무 깁니다", new SQLException("22001"));

        assertThat(PlaceIngestService.isDuplicate(e)).isFalse();
    }

    @Test
    @DisplayName("원인이 없으면 중복이 아니다")
    void 원인_없음() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("알 수 없음");

        assertThat(PlaceIngestService.isDuplicate(e)).isFalse();
    }

    private static DataIntegrityViolationException wrapped(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "제약을 어겼습니다", new SQLException("23505"), constraintName);
        return new DataIntegrityViolationException(cause.getMessage(), cause);
    }
}
