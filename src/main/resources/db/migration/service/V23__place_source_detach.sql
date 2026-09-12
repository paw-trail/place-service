-- 관리자가 떼어낸 소스를 기록합니다.
--
-- DELETE /api/v1/admin/places/{id}/sources/{sourceId} 가 여기에 한 행을 남깁니다.
--
-- 이 표가 없으면 분리가 다음 적재에 되돌려집니다.
-- place_source_link 를 하드 딜리트하므로 그 소스 레코드는 다음 수집에서
-- findBySourceAndSourceId 에 걸리지 않아 "처음 보는 레코드" 가 되고,
-- address_normalized 가 그대로라 매처가 같은 장소로 다시 붙입니다.
-- 관리자 화면이 "소스를 떼어내면 그 장소는 다시 병합되지 않습니다" 라고
-- 약속하고 있는데 그 약속을 지키는 장치가 이 표입니다.
--
-- 감사 표가 아니라 규칙 표입니다.
-- 적재가 돌 때마다 매처가 이 표를 읽어 후보에서 그 장소를 걸러냅니다.
-- 행을 지우면 감사 기록이 사라지는 것이 아니라 분리가 풀립니다.

CREATE TABLE place_source_detach
(
    id          uuid         PRIMARY KEY,

    -- 외래키를 걸지 않습니다. 전 테이블 공통 규약입니다.
    --
    -- 떼어낸 장소입니다. 소스 레코드 자체가 아니라 "이 소스를 이 장소에 붙이지 말라" 는 뜻이라
    -- 장소와 소스의 조합이 이 표의 단위입니다.
    place_id    uuid         NOT NULL,

    -- place_source_link 와 같은 값입니다.
    source      varchar(20)  NOT NULL,
    source_id   varchar(200) NOT NULL,

    -- 뗀 관리자입니다.
    --
    -- BaseEntity 를 상속하지 않아 JPA Auditing 이 채우지 않습니다.
    -- 컨트롤러가 인증 주체에서 꺼내 서비스로 넘깁니다.
    --
    -- 폭이 45 인 것은 다른 표의 created_by 와 맞춘 것입니다.
    -- 나중에 이 값으로 조인하거나 대조할 때 타입이 갈리면 안 됩니다.
    detached_by varchar(45)  NOT NULL,

    detached_at timestamp    NOT NULL
);

-- 같은 조합을 두 번 넣지 못하게 합니다.
--
-- 뗀 소스가 새 장소로 갈라진 뒤 그 장소에서 또 떼는 것은 다른 조합이라 막히지 않습니다.
-- 막아야 하는 것은 같은 장소에 같은 소스를 두 번 기록하는 것뿐입니다.
CREATE UNIQUE INDEX uq_place_source_detach
    ON place_source_detach (place_id, source, source_id);

-- 매처가 쓰는 조회입니다.
--
-- 적재 한 건마다 "이 소스 레코드가 분리된 장소가 어디인가" 를 묻습니다.
-- place_id 가 앞에 오는 위 UNIQUE 인덱스로는 이 조회를 탈 수 없어 따로 둡니다.
--
-- 적재는 17,000 건을 도는 배치라 건마다 왕복이 늘면 그대로 시간이 됩니다.
CREATE INDEX idx_place_source_detach_source
    ON place_source_detach (source, source_id);

COMMENT ON TABLE place_source_detach IS '관리자가 떼어낸 소스. 매처가 읽어 다시 붙지 않게 합니다.';
