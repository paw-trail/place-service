-- 이 서비스의 첫 마이그레이션 스크립트입니다.
-- V1 부터 V19 는 공통 모듈이 사용하는 대역이므로 쓰지 않습니다.
--
-- 이미 적용된 스크립트는 수정하지 않습니다.
-- 내용이 바뀌면 체크섬이 달라져 다음 기동이 실패합니다.
-- 변경이 필요하면 다음 번호로 새 스크립트를 만듭니다.
--
-- place_db 에는 이 네 테이블과 공통 대역의 outbox 만 있습니다.
-- 반려동물 동반 조건은 한 컬럼도 들어오지 않습니다.
-- 그것은 policy_db 의 소유이며, place 는 "장소가 무엇인가" 만 답합니다.
--
-- PostGIS 확장이 필요합니다.
-- geom 컬럼이 geography(Point) 타입이라 확장이 없으면 이 스크립트가 실패합니다.
-- infra 의 init-db/02-extensions.sh 가 place_db 에 미리 넣어 두며,
-- 테스트는 PlaceApplicationTests 가 postgis/postgis 이미지를 띄웁니다.

-- =============================================================================
-- place
-- =============================================================================
-- 장소 마스터입니다. 소스 넷에서 모은 것을 병합해 장소당 한 행으로 둡니다.
--
-- 같은 장소가 여러 소스에 있으면 하나로 합쳐 사용자에게 한 번만 보여줍니다.
-- 어느 소스가 이 행을 채웠는지는 place_source_link 가 들고 있습니다.
--
-- 이 표를 id 가 아닌 것으로 찾는 자리는 병합 매칭뿐입니다.
-- 검색은 search_db 가 담당하고, place 는 GET /places/{id} 와
-- GET /internal/places?ids= 로 PK 조회만 받습니다.
-- 인덱스를 셋만 두는 근거가 그것입니다.

CREATE TABLE place
(
    -- PK 는 모든 테이블이 uuid 입니다.
    -- 애플리케이션이 Hibernate 의 @UuidGenerator(style = VERSION_7) 로 생성해 넣으므로
    -- 여기에 기본값을 지정하지 않습니다.
    --
    -- 이 값은 한 번 밖으로 나가면 되돌릴 수 없습니다.
    -- 즐겨찾기 · 방문 기록 · 일정 · 후기 · 제보가 전부 이 값을 물고 갑니다.
    -- 그래서 병합 판정을 적재 시점에 끝내고, 매칭 전 상태의 행을 만들지 않습니다.
    id                 uuid          PRIMARY KEY,

    -- 사용자에게 보이는 이름입니다. 대표 소스(place_source_link.is_primary) 의 값입니다.
    name               varchar(200)  NOT NULL,

    -- 매칭용으로 정규화한 이름입니다.
    -- 지점명은 보존합니다. "강남점" 과 "홍대점" 은 다른 장소이기 때문입니다.
    --
    -- NULL 을 허용하는 것은 정규화가 실패할 수 있어서가 아니라,
    -- 정규화 규칙을 나중에 바꿔 다시 채울 여지를 남기기 위해서입니다.
    name_normalized    varchar(200),

    -- 괄호 별칭입니다. 송파나루공원(석촌호수) 에서 [석촌호수] 를 뽑아 둡니다.
    -- 소스마다 같은 장소를 본명과 별칭으로 달리 부르는 경우가 있어 매칭 축으로 씁니다.
    name_alias         text[],

    address_road       varchar(300),

    -- 지번 주소입니다. 문화정보원은 100% 채우고 공사 계열은 0% 입니다.
    -- 도로명이 없는 행의 주소 폴백으로 씁니다.
    address_jibun      varchar(300),

    -- 매칭 1순위 키입니다.
    -- 도로명 주소를 정규화한 값이며, 도로명이 없으면 지번에서 만듭니다.
    --
    -- 이 값이 틀리면 병합이 통째로 틀립니다.
    -- 시도명 표준화 하나만 빠져도 실측에서 병합 쌍 27 개가 누락됐습니다.
    address_normalized varchar(300),

    -- 법정동 코드의 시도 부분입니다. 공사 응답의 lDongRegnCd 에서 뽑습니다.
    -- 관광공사의 areaCode 가 아닙니다. 둘은 값 체계가 다릅니다.
    --
    -- 법정동 코드는 시도 2 + 시군구 3 + 읍면동 3 + 리 2 구조라
    -- 길이와 무관하게 앞 두 자리가 시도를 유일하게 결정합니다.
    -- 소스가 5 자리를 주는 행이 실제로 있으므로(세종특별자치시)
    -- 자르는 것이 아니라 시도 부분을 뽑는 것으로 규칙을 세웁니다.
    --
    -- 폭을 늘려 원본을 통째로 담지 않는 이유는 값의 길이가 행마다 갈리기 때문입니다.
    -- 지역 필터가 sido_code = '36' 으로는 못 찾고 LIKE '36%' 를 써야 하게 되며,
    -- 오류가 나지 않아 그 시도만 조용히 검색에서 빠집니다.
    sido_code          varchar(2),

    -- 법정동 코드의 시군구 부분입니다. 공사 응답의 lDongSignguCd 를 그대로 담습니다.
    --
    -- 공사 자체 코드인 sigungucode 를 쓰지 않는 이유는 두 가지입니다.
    -- 채움률이 절반뿐이고, 공사 전용이라 다른 소스 셋에서 채울 방법이 없습니다.
    --
    -- 혼잡도 API 의 signguCd 와 같은 체계인지는 아직 확인하지 않았습니다.
    -- congestion 을 붙일 때 실제로 호출해 확인하고, 어긋나면 그때 변환표를 둡니다.
    sigungu_code       varchar(5),

    -- 좌표입니다. 공사 응답에서 mapY 가 위도, mapX 가 경도로 순서가 반대입니다.
    --
    -- NOT NULL 인 이유는 좌표가 없으면 지도에 못 띄우고 거리 계산도 안 되기 때문입니다.
    -- 지오코딩까지 실패한 행이 생기면 그것은 데이터 오류이므로 DB 가 막습니다.
    lat                numeric(10,7) NOT NULL,
    lon                numeric(10,7) NOT NULL,

    -- ORIGINAL · CONVERTED · GEOCODED
    --   ORIGINAL   소스가 준 측량 좌표 그대로
    --   CONVERTED  EPSG:5174 에서 4326 으로 변환한 것
    --   GEOCODED   주소를 카카오 API 로 좌표화한 것
    --
    -- 병합 임계값이 값마다 다릅니다.
    -- 지오코딩 좌표끼리는 병합하지 않습니다.
    -- 오차가 겹치면 서로 다른 장소가 같은 좌표로 보이기 때문입니다.
    coord_source       varchar(10),

    -- 병합의 좌표 근접 매칭에 씁니다. lat · lon 에서 만듭니다.
    --
    -- lat · lon 과 값이 겹치지만 용도가 다릅니다.
    -- lat · lon 은 API 응답에 나가는 값이고 geom 은 매칭 전용입니다.
    --
    -- ST_DWithin 이 미터 단위 거리를 정확히 계산해 줍니다.
    -- lat · lon 으로 사각형 범위를 만들려면 "위도 1 도가 몇 미터인가" 를 직접 계산해야 하는데
    -- 그 값이 위도마다 다릅니다.
    --
    -- 엔티티의 @PrePersist · @PreUpdate 로 lat · lon 에서 만들어
    -- 좌표를 고칠 때 두 곳이 어긋나지 않게 합니다.
    geom               geography(Point),

    -- 공사 원천 분류입니다. 가공하지 않고 그대로 담습니다.
    --
    -- place_type 과 분리하는 이유는 소스가 넷이라 분류 체계가 제각각이기 때문입니다.
    -- 고캠핑에는 lclsSystm 이 없고 문화정보원은 자체 카테고리를 씁니다.
    -- 원천 분류를 남겨 두면 place_type 매핑 규칙을 나중에 바꿔도 다시 뽑을 수 있습니다.
    lcls1              varchar(30),
    lcls2              varchar(30),
    lcls3              varchar(30),

    -- 서비스 카테고리 9 종입니다.
    -- PARK · CAMPING · CAFE · STAY · CULTURE · LEISURE · RESTAURANT · ETC · VET
    --
    -- CHECK 제약을 걸지 않는 것은 의도입니다.
    -- 나중에 값이 늘어도 마이그레이션 없이 되며 검증은 애플리케이션이 합니다.
    place_type         varchar(12)   NOT NULL,

    -- 전화번호입니다.
    -- 공사 목록과 공통 응답은 전 계열 0% 이고 상세 응답의 infocenter 에서만 나옵니다.
    tel                varchar(30),

    -- 전화번호가 어느 소스에서 왔는지입니다.
    -- 소스마다 채움률이 달라 보완 순서를 판단하는 데 씁니다.
    tel_source         varchar(16),

    homepage           text,

    -- 예약 주소입니다. 고캠핑이 주로 채웁니다.
    reservation_url    text,

    -- 대표 사진입니다. 공사 firstimage 에서 옵니다.
    --
    -- 같은 목록 안에서도 행마다 http 와 https 가 섞여 오므로
    -- 적재할 때 https 로 맞춥니다. 브라우저가 혼합 콘텐츠를 막기 때문입니다.
    image_url          text,

    -- 공사 응답의 cpyrhtDivCd 입니다. Type1 · Type3 등이 행마다 갈립니다.
    --
    -- 지금 이 값을 읽는 화면은 없습니다.
    -- Type1(출처표시) 과 Type3(출처표시 + 변경금지) 둘 다
    -- 이미지를 변형 없이 그대로 띄우는 우리 쓰임에는 제약이 없습니다.
    --
    -- 그래도 지금 넣는 것은 되돌리기 비용이 비대칭이기 때문입니다.
    -- 지금은 이 스크립트에 한 줄이면 끝나지만,
    -- 나중에 넣으려면 마이그레이션에 전량 재적재가 따라붙습니다.
    cpyrht_div_cd      varchar(10),

    overview           text,

    -- 영업시간입니다. 문화정보원이 100% 채웁니다.
    -- 폭은 실측 없이 정한 값이라 넘치면 다음 번호로 넓힙니다.
    business_hours     varchar(200),

    closed_days        varchar(100),

    -- ACTIVE · CLOSED · UNKNOWN
    --
    -- 폐업한 장소도 행을 지우지 않고 CLOSED 로 표시합니다.
    -- 지우면 즐겨찾기 · 방문 기록 · 후기의 참조가 끊깁니다.
    --
    -- CLOSED 로 바꿀 신호는 공사 목록의 showflag 가 해제되는 것 하나뿐입니다.
    -- 그래서 수집이 areaBasedList2 가 아니라 petTourSyncList2 를 씁니다.
    status             varchar(10)   NOT NULL,

    -- 편의점 · 마트처럼 동선 중에 들르는 보급 지점인지입니다.
    -- 여정 지도가 place_type 과 이 값으로 마커 색을 가릅니다.
    supply_point       boolean       NOT NULL,

    -- true 면 수집 배치가 이 행의 UPDATE 를 건너뛰고
    -- 발견한 값을 place_pending_update 에 쌓습니다.
    --
    -- 관리자가 PATCH /api/v1/admin/places/{placeId} 로 고치면 켜집니다.
    -- 규칙을 한 문장으로 하면 "생성은 배치가 자유롭게, 수정과 삭제는 사람의 확인을 거쳐서" 입니다.
    admin_locked       boolean       NOT NULL,

    -- 소스가 준 데이터 기준일입니다.
    --
    -- 소스마다 한 값으로 고정된 것이 아니라 행마다 다릅니다.
    -- 문화정보원만 해도 2025-03-24 판과 2022-11-30 판이 섞여 있습니다.
    --
    -- 이 프로젝트에서 date 타입을 쓰는 몇 안 되는 자리입니다.
    -- 소스가 날짜만 주므로 시각을 채울 근거가 없습니다.
    data_base_date     date,

    -- 공통 모듈의 BaseEntity 가 매핑하는 여섯 컬럼입니다.
    -- 빠뜨리면 ddl-auto: validate 가 기동을 막습니다.
    created_at         timestamp     NOT NULL,
    created_by         varchar(45)   NOT NULL,
    updated_at         timestamp     NOT NULL,
    updated_by         varchar(45)   NOT NULL,

    -- 이 테이블에서는 사용하지 않고 항상 NULL 입니다.
    -- 폐업은 status = 'CLOSED' 하나로만 표현합니다.
    -- 둘 다 쓰면 조회 조건이 두 갈래로 갈려 한쪽만 고쳤을 때
    -- 폐업한 장소가 검색에 남는 사고가 납니다.
    deleted_at         timestamp,
    deleted_by         varchar(45)
);

-- 병합 1 순위 조회입니다. 도로명 주소를 정규화한 값이 일치하는 후보를 찾습니다.
CREATE INDEX idx_place_address_normalized
    ON place (address_normalized);

-- 병합에서 이름 일치를 확인합니다.
-- 주소나 좌표가 맞아도 이름이 다르면 병합하지 않으므로 모든 판정 단계가 이 인덱스를 거칩니다.
CREATE INDEX idx_place_name_normalized
    ON place (name_normalized);

-- 병합의 좌표 근접 조회입니다. ST_DWithin 이 이 인덱스를 씁니다.
--
-- 인덱스 셋이 이 서비스 성능의 전부입니다.
-- 병합은 이만 건 넘는 행을 도는 배치라 인덱스가 없으면 건마다 전체 스캔이 됩니다.
CREATE INDEX idx_place_geom
    ON place USING GIST (geom);

COMMENT ON TABLE place IS '장소 마스터. 반려동물 동반 조건은 policy_db 소유입니다.';

-- =============================================================================
-- place_source_link
-- =============================================================================
-- 이 장소가 어느 소스의 어느 레코드에서 왔는지입니다.
-- 장소 하나에 소스가 여럿 붙습니다.
--
-- 규칙은 "데이터셋 하나 = source 값 하나, 출처가 다르면 절대 합치지 않음" 입니다.
-- PET_TOUR 와 GOCAMPING 이 같은 한국관광공사인데도 값을 나눠 둔 것이 그 선례입니다.
--
-- 장소 매칭에서 거짓 병합이 거짓 분리보다 훨씬 위험합니다.
-- 거짓 병합은 강남점 조건에 홍대점 원문을 붙이는 식으로 틀린 근거를 만드는데,
-- 근거 제시가 정체성인 서비스에 치명적입니다. 그래서 애매하면 병합하지 않습니다.

CREATE TABLE place_source_link
(
    id           uuid          PRIMARY KEY,

    -- 외래키를 걸지 않습니다. 전 테이블 공통 규약입니다.
    place_id     uuid          NOT NULL,

    -- PET_TOUR · GOCAMPING · CULTURE_CSV · MOIS_VET
    --
    -- 사람이 읽는 이름(sourceLabel) 은 코드 상수이며 컬럼으로 두지 않습니다.
    -- 기관 단위로 묶어 조회하는 화면이나 API 가 하나도 없기 때문입니다.
    source       varchar(20)   NOT NULL,

    -- 소스가 주는 식별자입니다.
    -- 공사와 고캠핑은 contentId, 행안부 CSV 는 관리번호입니다.
    --
    -- 문화정보원 CSV 는 식별자 컬럼이 아예 없어 ingest 가 시설명|지번주소 로 만들어 넘깁니다.
    -- 그 조합이 실측에서 고유했고 구분자가 값에 한 번도 나오지 않았습니다.
    source_id    varchar(100)  NOT NULL,

    -- place 본체를 채울 때 어느 소스가 이겼는지입니다.
    --
    -- 필드 우선순위가 필드 단위가 아니라 소스 단위라는 뜻입니다.
    -- DELETE /admin/places/{id}/sources/{sourceId} 로 대표를 떼면
    -- 나머지 중 하나를 대표로 승격해야 하는데 그 판단에도 씁니다.
    is_primary   boolean       NOT NULL,

    -- 어느 단계에서 붙었는지입니다.
    --   PRIMARY          이 소스가 이 장소를 처음 만듦
    --   ADDRESS          주소 정규화 일치 + 이름 일치
    --   COORD_ORIGINAL   원본 좌표 100m 근접 + 이름 일치
    --   COORD_GEOCODED   한쪽이 지오코딩 좌표일 때 300m 근접 + 이름 일치
    --
    -- 이름 일치가 모든 단계의 전제입니다.
    -- 주소가 같고 이름이 다른 쌍이 실측에 백여 건 있었습니다.
    -- 북촌8경과 북촌문화센터, 스타필드코엑스몰과 다이소코엑스몰점 같은 것들입니다.
    match_method varchar(20)   NOT NULL,

    -- 매칭 신뢰도입니다.
    -- COORD_ORIGINAL 은 거리로 나눠 담습니다. 0~50m 를 높게, 50~100m 를 낮게 둡니다.
    -- 검증할 때 낮은 것부터 뽑아 눈으로 확인하기 위해서입니다.
    confidence   numeric(3,2),

    linked_at    timestamp     NOT NULL,

    created_at   timestamp     NOT NULL,
    created_by   varchar(45)   NOT NULL,
    updated_at   timestamp     NOT NULL,
    updated_by   varchar(45)   NOT NULL,
    deleted_at   timestamp,
    deleted_by   varchar(45)
);

-- 같은 소스 레코드가 두 장소에 붙는 것을 막습니다.
--
-- 재수집이 멱등이 되는 근거이기도 합니다.
-- 같은 수집 결과를 여러 번 밀어 넣어도 이 제약이 행을 하나로 유지합니다.
CREATE UNIQUE INDEX uq_place_source
    ON place_source_link (source, source_id);

-- 장소 상세의 sources[] 와 원문 보기가 이 조회를 씁니다.
CREATE INDEX idx_place_source_link_place
    ON place_source_link (place_id);

COMMENT ON TABLE place_source_link IS '장소가 어느 소스에서 왔는지. 병합의 근거입니다.';

-- =============================================================================
-- place_facility
-- =============================================================================
-- 장소의 편의시설입니다. 값이 없으면 화면에서 편의시설 섹션 자체를 표시하지 않습니다.
--
-- BaseEntity 를 상속하지 않고 감사 컬럼도 두지 않습니다.
-- 배치가 만들고 지우는 순수 연결 표라 시각을 볼 사람이 없습니다.

CREATE TABLE place_facility
(
    place_id      uuid        NOT NULL,

    -- 출처가 확인된 값은 넷입니다.
    --   PARKING        문화정보원 「주차 가능여부」. Y · N 으로만 오는 정형 값입니다.
    --   WALKING_TRAIL  고캠핑 posblFcltyCl 의 "산책로"
    --   PLAYGROUND     고캠핑 sbrsCl 의 "놀이터"
    --   RESERVATION    고캠핑 resveCl 의 "온라인실시간예약"
    --
    -- 명세에 있던 EMERGENCY_24H 는 뺐습니다.
    -- 인허가 데이터에 24 시나 영업시간 컬럼이 없고, 사업장명의 "24" 로 채우면
    -- 양방향으로 틀리는데 검증할 방법이 없습니다.
    -- 응급 상황에서 틀리면 대가가 가장 큰 자리라 "판정 없음" 을 택했습니다.
    --
    -- OUTDOOR_SEAT 은 채울 값이 아직 없습니다.
    -- 화면 필터를 뺄지 문구를 바꿀지가 정해지면 되살아납니다.
    --
    -- CHECK 를 걸지 않으므로 값이 늘어도 이 스크립트는 바뀌지 않습니다.
    -- 값 목록은 문서와 코드의 문제이지 스키마의 문제가 아닙니다.
    facility_code varchar(24) NOT NULL,

    PRIMARY KEY (place_id, facility_code)
);

COMMENT ON TABLE place_facility IS '장소별 편의시설. 배치가 만들고 지우는 연결 표입니다.';

-- =============================================================================
-- place_pending_update
-- =============================================================================
-- admin_locked 이 켜진 행에 대해 수집 배치가 발견했으나 반영하지 못한 값입니다.
--
-- 판단 주체가 place 라는 점이 중요합니다.
-- ingest 가 POST /internal/places/bulk 를 부르면
-- place 가 잠긴 행을 건너뛰고 대기 행을 만듭니다. 서비스 경계를 넘지 않습니다.

CREATE TABLE place_pending_update
(
    id            uuid          PRIMARY KEY,

    place_id      uuid          NOT NULL,

    -- 어느 필드가 다른지입니다. tel · status · homepage 등입니다.
    field_name    varchar(40)   NOT NULL,

    -- 지금 값과 수집이 가져온 값입니다.
    --
    -- 폭이 report.reported_value 와 같습니다.
    -- 두 표가 하는 일이 같기 때문입니다. place 의 한 필드 값이 들어오고
    -- field_name 도 양쪽 다 varchar(40) 입니다.
    current_value varchar(500),
    new_value     varchar(500),

    -- 어느 소스가 가져온 값인지입니다.
    source        varchar(20)   NOT NULL,

    detected_at   timestamp     NOT NULL,

    -- PENDING · APPROVED · REJECTED
    --
    -- 이름을 resolved 로 두지 않은 것은 값이 셋이라 boolean 처럼 읽히기 때문입니다.
    -- report.status 와도 형태가 맞습니다.
    status        varchar(12)   NOT NULL,

    -- 처리한 관리자와 처리 시각입니다. 미처리면 둘 다 NULL 입니다.
    --
    -- report 에는 reviewed_by · reviewed_at 이 있는데
    -- 같은 성격의 관리자 처리인 여기만 기록이 없었습니다.
    -- 자동 처리와 사람 판단이 갈리는 자리에는 기록을 남긴다는 원칙을 따릅니다.
    resolved_by   varchar(45),
    resolved_at   timestamp,

    created_at    timestamp     NOT NULL,
    created_by    varchar(45)   NOT NULL,
    updated_at    timestamp     NOT NULL,
    updated_by    varchar(45)   NOT NULL,
    deleted_at    timestamp,
    deleted_by    varchar(45)
);

-- GET /api/v1/admin/places/pending 이 이 조회를 씁니다.
CREATE INDEX idx_place_pending_place
    ON place_pending_update (place_id);

COMMENT ON TABLE place_pending_update IS '잠긴 행에 대해 배치가 반영하지 못한 값. 관리자가 승인하거나 반려합니다.';
