package com.pawtrail.place.application.service;

import com.pawtrail.place.application.dto.input.PlaceDraft;
import com.pawtrail.place.domain.provider.GeocodingProvider;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 좌표가 없는 건을 적재하기 전에 미리 지오코딩합니다.
 *
 * 별도 빈인 이유가 트랜잭션 경계 때문입니다.
 *
 * 적재는 청크 하나를 한 트랜잭션으로 묶습니다.
 * 그 안에서 카카오를 부르면 응답을 기다리는 동안 데이터베이스 커넥션이 점유됩니다.
 * 시간 제한을 걸어 두어도 마찬가지이고,
 * 청크를 여러 개 동시에 보내면 커넥션 풀이 그만큼 빨리 마릅니다.
 *
 * 같은 빈 안의 메서드로 두면 자기 호출이 되어 소용이 없습니다.
 * 스프링이 프록시로 가로채는 것은 빈 바깥에서 들어오는 호출뿐입니다.
 *
 * 여기에는 @Transactional 을 붙이지 않습니다.
 * 데이터베이스를 전혀 건드리지 않으므로 트랜잭션이 필요 없습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoordinatePrefiller {

    private final GeocodingProvider geocodingProvider;

    /**
     * 좌표가 없는 건을 지오코딩해 채운 목록을 돌려줍니다.
     *
     * 짝이 있는지 보지 않습니다.
     * 그것을 확인하려면 데이터베이스를 읽어야 하는데 그러면 나눈 뜻이 없어집니다.
     * 짝이 있는데도 카카오를 부르는 경우가 생기지만
     * 적재본 17,480 건 가운데 좌표가 없는 것이 아홉 건이라 헛 호출의 상한이 그 아홉입니다.
     *
     * 실패하면 원본을 그대로 둡니다.
     * 그 건은 적재 단계에서 짝을 찾지 못하면 건너뜁니다.
     */
    public List<PlaceDraft> prefill(List<PlaceDraft> drafts) {
        List<PlaceDraft> prepared = new ArrayList<>(drafts.size());
        for (PlaceDraft draft : drafts) {
            if (!draft.needsGeocoding()) {
                prepared.add(draft);
                continue;
            }
            prepared.add(geocodingProvider.geocode(draft.geocodingAddress())
                    .map(found -> draft.withGeocoded(found.lat(), found.lon()))
                    .orElse(draft));
        }
        return prepared;
    }
}
