-- 반영 대기 값의 인덱스를 교체합니다.
--
-- 종전에는 place_id 하나였습니다.
-- 반려 이력 조회가 생기면서 field_name 과 status 까지 보게 되었습니다.
--
-- 반려한 값을 다음 수집에서 다시 쌓지 않기로 했습니다.
-- 소스가 값을 고치지 않는 한 같은 차이가 수집마다 발견되는데,
-- 그때마다 대기 행을 만들면 관리자가 같은 것을 계속 반려하게 되고
-- 목록이 노이즈로 차 승인해야 할 것이 묻힙니다.
--
-- place_id 가 그대로 선두라 종전 조회는 손해를 보지 않습니다.
--
-- 목록 조회(status 가 PENDING 인 것을 detected_at 순으로)는 이 인덱스를 타지 않습니다.
-- 그쪽에 (status, detected_at) 을 따로 두지 않은 것은
-- 이 표가 관리자가 손댄 장소에서만 행이 생겨 지금 0 건이기 때문입니다.
-- 효과를 잴 부하가 없으면 넣지 않는다는 기준을 따랐고, 필요해지면 새 번호로 붙입니다.

DROP INDEX idx_place_pending_place;

CREATE INDEX idx_place_pending_place
    ON place_pending_update (place_id, field_name, status);
