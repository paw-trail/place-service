package com.pawtrail.place.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이 서비스의 설정 프로퍼티를 등록합니다.
 *
 * @ConfigurationProperties 만 붙여서는 빈이 되지 않습니다.
 * 이 목록에 넣거나 스캔을 켜야 합니다.
 * user 가 LlmProperties 를 만들고 여기에 안 넣어 기동이 실패한 적이 있습니다.
 *
 * 진입점에 @ConfigurationPropertiesScan 을 붙이지 않는 이유는
 * PlaceApplication 이 복제 후 고칠 문자열을 줄이려고 스캔 범위를 안 적어 두었기 때문입니다.
 *
 * 프로퍼티 클래스를 새로 만들면 반드시 여기에 함께 넣을 것.
 */
@Configuration
@EnableConfigurationProperties({
        KakaoProperties.class
})
public class PlaceConfig {
}
