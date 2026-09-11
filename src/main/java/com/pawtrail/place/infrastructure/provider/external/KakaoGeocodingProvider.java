package com.pawtrail.place.infrastructure.provider.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawtrail.place.domain.provider.GeocodingProvider;
import com.pawtrail.place.infrastructure.config.KakaoProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 API 로 주소를 좌표로 바꿉니다.
 *
 * 이 서비스가 바깥을 부르는 유일한 자리입니다.
 */
@Slf4j
@Component
public class KakaoGeocodingProvider implements GeocodingProvider {

    private static final String ADDRESS_PATH = "/v2/local/search/address.json";

    // 카카오는 인증 방식 이름을 접두사로 요구합니다
    private static final String AUTH_PREFIX = "KakaoAK ";

    private final RestClient restClient;
    private final KakaoProperties properties;

    /**
     * 빌더를 손으로 받는 이유가 둘입니다.
     *
     * 하나는 @Qualifier 때문입니다.
     * 롬복의 @RequiredArgsConstructor 가 만드는 생성자에는 애노테이션이 붙지 않아
     * 어느 빌더를 받을지 지정할 수 없습니다.
     *
     * 다른 하나는 이 클라이언트만의 기본값 때문입니다.
     * 인증 헤더와 시간 제한을 여기서 한 번 정해 두면 부르는 자리마다 안 적어도 됩니다.
     *
     * 바깥 API 이므로 externalRestClientBuilder 를 받습니다.
     * internal 쪽은 lb:// 를 풀고 우리 인증 헤더를 싣는데
     * 그 헤더가 카카오로 나가면 안 됩니다.
     */
    public KakaoGeocodingProvider(
            @Qualifier("externalRestClientBuilder") RestClient.Builder builder,
            KakaoProperties properties) {

        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.localBaseUrl())
                .defaultHeader("Authorization", AUTH_PREFIX + safeKey(properties))
                .requestFactory(timeoutFactory(properties.timeoutSeconds()))
                .build();
    }

    /**
     * 주소로 좌표를 찾습니다.
     *
     * 카카오는 도로명과 지번을 모두 받고 둘 다 있으면 도로명을 우선 돌려줍니다.
     * 우리는 x 와 y 만 쓰므로 어느 쪽이 왔는지 가리지 않습니다.
     *
     * 응답의 x 가 경도이고 y 가 위도입니다.
     * 순서가 뒤집혀 있어 그대로 옮기면 좌표가 통째로 다른 곳을 가리키는데
     * 오류가 나지 않습니다.
     *
     * 실패를 삼키는 것이 의도입니다.
     * 적재는 청크로 도는 배치라 한 건의 실패가 전체를 멈추면 안 됩니다.
     * 카카오가 잠깐 죽었다고 만 칠천 건이 멈추는 편이 훨씬 나쁩니다.
     */
    @Override
    public Optional<Coordinate> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        if (!properties.usable()) {
            log.warn("카카오 키가 없어 지오코딩을 건너뜁니다: address={}", address);
            return Optional.empty();
        }

        try {
            AddressSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ADDRESS_PATH)
                            .queryParam("query", address)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(AddressSearchResponse.class);

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                log.warn("주소로 좌표를 찾지 못했습니다: address={}", address);
                return Optional.empty();
            }

            AddressDocument document = response.documents().getFirst();
            if (document.x() == null || document.y() == null) {
                return Optional.empty();
            }
            // x 가 경도이고 y 가 위도입니다
            return Optional.of(new Coordinate(
                    new BigDecimal(document.y()),
                    new BigDecimal(document.x())));

        } catch (Exception e) {
            log.warn("지오코딩에 실패했습니다: address={}", address, e);
            return Optional.empty();
        }
    }

    /**
     * 키가 없어도 클라이언트를 만들 수 있게 빈 문자열을 둡니다.
     *
     * 헤더 값이 null 이면 빌더가 그 자리에서 터져 앱이 안 뜹니다.
     * 키가 없는 것은 기동을 막을 일이 아니라 지오코딩을 건너뛸 일입니다.
     */
    private static String safeKey(KakaoProperties properties) {
        return properties.restApiKey() == null ? "" : properties.restApiKey();
    }

    /**
     * 시간 제한을 겁니다.
     *
     * ingest 의 PetTourApiClient 가 쓰는 것과 같은 방식입니다.
     * 연결과 읽기에 같은 값을 주는 이유는 지오코딩이 한 번에 한 건이라
     * 둘을 나눠 잡을 근거가 없기 때문입니다.
     */
    private static SimpleClientHttpRequestFactory timeoutFactory(int seconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(seconds));
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AddressSearchResponse(List<AddressDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AddressDocument(String x, String y) {
    }
}
