package com.pawtrail.place.domain.rule;

import com.pawtrail.place.domain.enums.SourceType;
import java.util.List;

/**
 * 병합할 때 어느 소스가 대표인지 정합니다.
 *
 * 값을 실제로 채우는 일은 Place.fillEmptyFrom 이 합니다.
 * "빈 칸만 채운다" 는 그 객체 자신이 가장 잘 아는 사실이기 때문입니다.
 * 이 클래스는 순서만 답합니다.
 */
public final class PlaceMerger {

    /**
     * 대표 소스 순서입니다.
     *
     * 채움률에는 영향이 없습니다.
     * 빈 칸을 서로 채워 주므로 어느 순서로 해도 병합 그룹의 채움률이 82.4% 로 같습니다.
     *
     * 영향이 있는 것은 두 소스가 모두 값을 가졌을 때의 품질입니다.
     * 적재본에서 overview 가 충돌하는 경우가 94% 인데
     * 문화정보원 값은 "관광지" 같은 카테고리 라벨인 반면
     * 공사 계열은 실제 소개문입니다.
     *
     * 전화번호만 이 순서에서 손해를 봅니다.
     * 공사는 관리사무소 번호를 주고 문화정보원은 그 장소 직통을 주는 경우가 있습니다.
     * 다만 공사 채움률이 92% 라 값이 없는 것보다는 낫습니다.
     */
    private static final List<SourceType> PRIORITY = List.of(
            SourceType.PET_TOUR,
            SourceType.GOCAMPING,
            SourceType.CULTURE_CSV,
            SourceType.MOIS_VET
    );

    private PlaceMerger() {
    }

    /**
     * 새로 들어온 소스가 기존 대표를 밀어내야 하는지 봅니다.
     *
     * 순서가 앞설 때만 참입니다.
     * 같은 소스면 밀어내지 않습니다. 먼저 들어온 것이 대표로 남습니다.
     *
     * 밀어낸다는 것은 place 본체의 값을 새 소스 것으로 바꾼다는 뜻이 아닙니다.
     * 이미 채워진 칸은 그대로 두고 is_primary 만 옮깁니다.
     * 값을 다시 쓰면 먼저 들어온 소스가 채운 것을 덮어써
     * 적재 순서에 따라 결과가 달라집니다.
     */
    public static boolean shouldTakeOver(SourceType incoming, SourceType currentPrimary) {
        if (incoming == null || currentPrimary == null) {
            return false;
        }
        return rankOf(incoming) < rankOf(currentPrimary);
    }

    /**
     * 소스 목록에서 대표가 될 것을 고릅니다.
     *
     * 소스 분리로 대표가 사라졌을 때 나머지 중 하나를 승격하는 자리에서 씁니다.
     */
    public static SourceType choosePrimary(List<SourceType> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        SourceType best = null;
        for (SourceType source : sources) {
            if (best == null || rankOf(source) < rankOf(best)) {
                best = source;
            }
        }
        return best;
    }

    /**
     * 순서에 없는 소스는 맨 뒤로 보냅니다.
     *
     * 소스가 늘어도 이 클래스를 고치지 않으면 조용히 틀리는 것이 아니라
     * 그냥 우선순위가 가장 낮은 것으로 다뤄집니다.
     */
    private static int rankOf(SourceType source) {
        int index = PRIORITY.indexOf(source);
        return index < 0 ? PRIORITY.size() : index;
    }
}
