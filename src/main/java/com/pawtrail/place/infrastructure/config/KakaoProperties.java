package com.pawtrail.place.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 API 설정입니다.
 *
 * 좌표가 없거나 대한민국 범위 밖인 장소를 주소로 지오코딩할 때 씁니다.
 *
 * 값이 비면 기동을 막지 않습니다.
 * 적재를 돌릴 때만 필요하고 그때 건너뛴 건수로 드러나며,
 * 이 설정 때문에 앱이 아예 안 뜨면 다른 작업까지 막힙니다.
 * 대신 지오코딩을 부르는 시점에 키가 없으면 그 건을 건너뛰고 경고를 남깁니다.
 */
@ConfigurationProperties(prefix = "app.kakao")
public record KakaoProperties(
        String restApiKey,
        String localBaseUrl,
        Integer timeoutSeconds) {

    public KakaoProperties {
        if (localBaseUrl == null || localBaseUrl.isBlank()) {
            throw new IllegalStateException("app.kakao.local-base-url 이 필요합니다.");
        }
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            throw new IllegalStateException("app.kakao.timeout-seconds 는 0보다 커야 합니다.");
        }
    }

    /**
     * 키가 없으면 지오코딩을 시도하지 않습니다.
     */
    public boolean usable() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
