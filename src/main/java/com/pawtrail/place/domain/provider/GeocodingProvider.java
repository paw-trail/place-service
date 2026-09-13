package com.pawtrail.place.domain.provider;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 주소를 좌표로 바꾸는 약속입니다.
 *
 * 이 인터페이스에는 카카오라는 단어가 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 하는지는 infrastructure 가 정합니다.
 *
 * 적재본에서 대상이 일곱 건입니다.
 *   좌표가 아예 없는 다섯 건
 *   좌표가 대한민국 범위 밖인 두 건
 * 나머지는 소스가 준 좌표를 그대로 씁니다.
 */
public interface GeocodingProvider {

    /**
     * 주소로 좌표를 찾습니다. 못 찾으면 비어 있습니다.
     *
     * 실패를 예외로 던지지 않습니다.
     * 부르는 쪽이 하는 일이 "못 찾으면 그 건을 건너뛴다" 하나라
     * 못 찾은 것과 호출이 실패한 것을 구분할 이유가 없습니다.
     */
    Optional<Coordinate> geocode(String address);

    /**
     * 찾아낸 좌표입니다.
     */
    record Coordinate(BigDecimal lat, BigDecimal lon) {
    }
}
