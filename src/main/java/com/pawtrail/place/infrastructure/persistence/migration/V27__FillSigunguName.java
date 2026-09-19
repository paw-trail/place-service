package com.pawtrail.place.infrastructure.persistence.migration;

import com.pawtrail.place.domain.rule.AddressNormalizer;
import com.pawtrail.place.domain.rule.Sido;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * 이미 있는 장소의 시군구 이름을 한 번 채웁니다.
 *
 * V26 이 칸을 이름으로 바꾸었고, 앞으로 들어오는 장소는 적재와 관리자 수정이 채웁니다.
 * 이미 있는 행은 다시 적재해도 채워지지 않습니다.
 * 주소가 이미 있는 행은 fillEmptyFrom 이 지역 값을 건드리지 않기 때문입니다.
 *
 * SQL 이 아니라 자바로 둡니다.
 * 주소에서 시군구를 뽑는 규칙이 AddressNormalizer 에 있고,
 * SQL 로 다시 쓰면 같은 규칙이 두 곳에 생겨 어느 쪽이 맞는지 알 수 없게 됩니다.
 *
 * 빈으로 등록합니다.
 * 스프링 부트가 JavaMigration 빈을 모아 Flyway 에 넣어 주므로
 * db/migration 폴더가 아니라 이 계층 안에 둘 수 있습니다.
 * 클래스 이름이 곧 버전과 설명이라 V27__ 로 시작해야 합니다. 이름을 바꾸면 버전이 바뀝니다.
 *
 * 어느 환경이든 기동할 때 한 번 돕니다.
 * 사람이 따로 눌러야 하는 길로 두면 누군가의 데이터베이스에서 조용히 빈 채로 남습니다.
 * 여러 대가 함께 떠도 Flyway 가 잠금을 잡아 한 번만 돕니다.
 * 테스트 컨테이너는 빈 데이터베이스라 0 행을 돕니다.
 *
 * 주소에 시도가 없는 행은 저장된 시도 코드를 폴백으로 씁니다.
 * 적재할 때 소스가 따로 준 시도(고캠핑 doNm 등)로 채운 값이라 적재 때와 같은 답이 나옵니다.
 *
 * updated_at 은 건드리지 않습니다.
 * 장소 값이 바뀐 것이 아니라 비어 있던 파생 값을 채우는 것이라 수정으로 치지 않습니다.
 */
@Slf4j
@Component
public class V27__FillSigunguName extends BaseJavaMigration {

    // 한 번에 보내는 UPDATE 수입니다
    // 행이 이만 건 남짓이라 한 줄씩 보내면 왕복이 그만큼 생깁니다
    private static final int BATCH_SIZE = 500;

    private static final String SELECT_TARGETS =
            "SELECT id, address_road, address_jibun, sido_code FROM place WHERE sigungu_name IS NULL";

    private static final String UPDATE_NAME =
            "UPDATE place SET sigungu_name = ? WHERE id = ?";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        List<Target> targets = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_TARGETS)) {
            while (rs.next()) {
                targets.add(new Target(
                        rs.getObject("id", UUID.class),
                        rs.getString("address_road"),
                        rs.getString("address_jibun"),
                        rs.getString("sido_code")));
            }
        }

        int filled = 0;
        int pending = 0;
        try (PreparedStatement update = connection.prepareStatement(UPDATE_NAME)) {
            for (Target target : targets) {
                String name = AddressNormalizer.resolveSigunguName(
                        target.addressRoad(), target.addressJibun(), fallbackOf(target.sidoCode()));
                if (name == null) {
                    continue;
                }
                update.setString(1, name);
                update.setObject(2, target.id());
                update.addBatch();
                filled++;
                pending++;
                if (pending == BATCH_SIZE) {
                    update.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                update.executeBatch();
            }
        }

        // 못 뽑은 행은 대부분 세종입니다. 시군구가 없는 자치단체라 비어 있는 것이 맞습니다
        log.info("시군구 이름을 채웠습니다: 대상 {}건 · 채움 {}건 · 비움 {}건",
                targets.size(), filled, targets.size() - filled);
    }

    // 저장된 시도 코드를 시도 표기로 바꿔 폴백으로 넘깁니다
    // 답할 수 없는 코드면 폴백 없이 주소만 봅니다
    private static String fallbackOf(String sidoCode) {
        Sido sido = Sido.fromCode(sidoCode);
        return sido == null ? null : sido.canonical();
    }

    private record Target(UUID id, String addressRoad, String addressJibun, String sidoCode) {
    }
}
