package com.example.demo.client;

import com.example.demo.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class OAuth2Client {

    private final WebClient webClient;

    @Value("${oauth2.client.id}")
    private String clientId;

    @Value("${oauth2.client.secret}")
    private String clientSecret;

    @Value("${oauth2.token.url:https://idm.stackspot.com/stackspot-freemium/oidc/oauth/token}")
    private String tokenUrl;

    private String cachedToken;
    private LocalDateTime tokenExpiration;

    public OAuth2Client(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    public Mono<String> getAccessToken() {
        if (cachedToken != null && tokenExpiration != null &&
                LocalDateTime.now().isBefore(tokenExpiration.minus(5, ChronoUnit.MINUTES))) {
            return Mono.just(cachedToken);
        }

        return requestNewToken()
                .map(this::cacheToken)
                .map(TokenResponse::accessToken);
    }

    private Mono<TokenResponse> requestNewToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", "client_credentials");

        return webClient.post()
                .uri(tokenUrl)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .doOnError(error -> {
                    System.err.println("Erro ao obter token OAuth2: " + error.getMessage());
                    error.printStackTrace();
                })
                .onErrorResume(error -> {
                    return Mono.error(new RuntimeException("Falha ao obter token OAuth2", error));
                });
    }

    private TokenResponse cacheToken(TokenResponse tokenResponse) {
        this.cachedToken = tokenResponse.accessToken();

        if (tokenResponse.expiresIn() != null) {
            this.tokenExpiration = LocalDateTime.now().plus(tokenResponse.expiresIn(), ChronoUnit.SECONDS);
        } else {
            this.tokenExpiration = LocalDateTime.now().plus(20, ChronoUnit.MINUTES);
        }

        return tokenResponse;
    }

    public void invalidateToken() {
        this.cachedToken = null;
        this.tokenExpiration = null;
    }
}
