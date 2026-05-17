package atracio.agent.config;

import atracio.agent.atracio.AtracioUrlResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configures the RestClient bean used by AtracioBackendClientHttp.
 *
 * Timeouts:
 *   connect : 5s  — fail fast if Atracio is unreachable
 *   read    : 30s — some Atracio list endpoints can be slow on large datasets
 *
 * The base URL is set from AtracioUrlResolver so all requests are rooted
 * at the correct tenant host without repetition in each method.
 *
 * No default Authorization header is set here — the bearer token is always
 * injected per-request by AtracioBackendClientHttp.
 */
@Configuration
public class HttpClientConfig {

    @Value("${atracio.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${atracio.http.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Bean
    public RestClient atracioRestClient(AtracioUrlResolver urlResolver) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(connectTimeoutMs).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(readTimeoutMs).toMillis());

        return RestClient.builder()
                .baseUrl(urlResolver.getApiBase())
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept",       "application/json")
                .build();
    }
}