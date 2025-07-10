package com.example.demo.client;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.demo.dto.TokenResponse;

import reactor.core.publisher.Mono;

@Component
public class StackspotClient implements ApiClient {

    private static final Logger log = LoggerFactory.getLogger(StackspotClient.class);

    private final WebClient webClient;

    // OAuth2 Configuration
    @Value("${oauth2.client.id}")
    private String clientId;

    @Value("${oauth2.client.secret}")
    private String clientSecret;

    @Value("${oauth2.token.url}")
    private String tokenUrl;

    // Token cache
    private String cachedToken;
    private LocalDateTime tokenExpiration;

    public StackspotClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // OAuth2 Token Management
    @Override
    public Mono<String> authenticate() {
        return getAccessToken();
    }

    public Mono<String> getAccessToken() {
        if (isTokenValid()) {
            log.debug("Usando token em cache");
            return Mono.just(cachedToken);
        }

        log.info("Solicitando novo token OAuth2");
        return requestNewToken()
                .map(this::cacheToken)
                .map(TokenResponse::accessToken);
    }

    private boolean isTokenValid() {
        return cachedToken != null && tokenExpiration != null &&
                LocalDateTime.now().isBefore(tokenExpiration.minus(5, ChronoUnit.MINUTES));
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
                .doOnSuccess(response -> log.info("Token OAuth2 obtido com sucesso"))
                .doOnError(error -> log.error("Erro ao obter token OAuth2: {}", error.getMessage()))
                .onErrorResume(error -> Mono.error(new RuntimeException("Falha ao obter token OAuth2", error)));
    }

    private TokenResponse cacheToken(TokenResponse tokenResponse) {
        this.cachedToken = tokenResponse.accessToken();

        if (tokenResponse.expiresIn() != null) {
            this.tokenExpiration = LocalDateTime.now().plus(tokenResponse.expiresIn(), ChronoUnit.SECONDS);
        } else {
            this.tokenExpiration = LocalDateTime.now().plus(20, ChronoUnit.MINUTES);
        }

        log.debug("Token armazenado em cache até: {}", tokenExpiration);
        return tokenResponse;
    }

    public void invalidateToken() {
        log.info("Invalidando cache do token");
        this.cachedToken = null;
        this.tokenExpiration = null;
    }

    // Chat/AI Integration
    public Mono<String> callChatEndpoint(List<String> uploadIds, String userPrompt, String jwt, String chatEndpoint) {
        log.info("Chamando endpoint de chat com {} arquivos", uploadIds.size());

        org.json.JSONObject body = new org.json.JSONObject();
        body.put("streaming", false);
        body.put("user_prompt", userPrompt);
        body.put("stackspot_knowledge", false);
        body.put("return_ks_in_response", true);
        body.put("upload_ids", new org.json.JSONArray(uploadIds));

        return webClient.post()
                .uri(chatEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Resposta do chat recebida com sucesso"))
                .doOnError(error -> log.error("Erro na chamada do chat: {}", error.getMessage()));
    }
}
