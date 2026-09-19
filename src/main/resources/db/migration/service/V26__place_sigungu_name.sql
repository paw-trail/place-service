-- 시군구를 코드가 아니라 이름으로 담습니다.
--
-- sigungu_code 는 V20 에서 한국관광공사 응답의 법정동 시군구 코드를 담으려던 칸이었습니다.
-- 그 코드는 한국관광공사만 주고, 고캠핑과 문화정보원은 이름을, 행정안전부는 다른 체계의 코드를 줍니다.
-- 적재 요청에 칸을 더해도 한 소스에서 온 장소만 채워지는 구조라 지금까지 전 행이 비어 있었습니다.
--
-- 주소에는 네 소스 모두 시군구가 들어 있습니다.
-- 그래서 주소에서 이름을 뽑아 담기로 했고, 칸 이름도 담는 값에 맞게 바꿉니다.
-- 코드가 아닌 값을 _code 칸에 두면 읽는 사람이 속습니다.
--
-- 폭 20 은 가장 긴 이름(창원시 마산합포구 · 9 자)의 두 배 남짓입니다.
-- 전 행이 비어 있어 옮길 값이 없고, 타입을 바꿔도 잃는 값이 없습니다.
--
-- 이미 있는 행은 V27 이 채웁니다.
-- 뽑는 규칙이 자바(AddressNormalizer)에 있어 SQL 로 옮겨 쓰지 않고 자바 마이그레이션이 같은 규칙을 부릅니다.
-- V27 은 이 폴더가 아니라 infrastructure/persistence/migration 에 있습니다.

ALTER TABLE place RENAME COLUMN sigungu_code TO sigungu_name;

ALTER TABLE place ALTER COLUMN sigungu_name TYPE varchar(20);

COMMENT ON COLUMN place.sigungu_name IS
    '시군구 이름. 주소에 적힌 대로 담으며 일반구는 시와 붙임(고양시 덕양구). 세종은 NULL';
