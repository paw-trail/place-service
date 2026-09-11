package com.pawtrail.place.application.service;

import com.pawtrail.place.application.dto.input.PlaceDraft;
import com.pawtrail.place.application.dto.output.BulkResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 적재를 두 단계로 잇습니다.
 *
 * <pre>
 * 1 단계   CoordinatePrefiller  트랜잭션 밖에서 지오코딩을 끝냄
 * 2 단계   PlaceIngestService   트랜잭션 안에서 저장만 함
 * </pre>
 *
 * 이 빈이 따로 있는 이유가 자기 호출 때문입니다.
 * 두 단계를 한 빈 안의 메서드로 두면 스프링이 @Transactional 을 가로채지 못합니다.
 * 프록시는 빈 바깥에서 들어오는 호출에만 걸립니다.
 * 그러면 트랜잭션 없이 저장이 돌아 청크 단위 원자성이 사라집니다.
 *
 * 여기에는 @Transactional 을 붙이지 않습니다.
 * 붙이면 1 단계의 외부 호출이 다시 트랜잭션 안으로 들어와 나눈 뜻이 없어집니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceBulkService {

    private final CoordinatePrefiller coordinatePrefiller;
    private final PlaceIngestService placeIngestService;

    public BulkResult ingest(List<PlaceDraft> drafts) {
        return placeIngestService.ingest(coordinatePrefiller.prefill(drafts));
    }
}
