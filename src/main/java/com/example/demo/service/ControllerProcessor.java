package com.example.demo.service;

import com.example.demo.client.S3UploadClient;
import com.example.demo.client.StackspotClient;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ControllerMatch;
import com.example.demo.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Optional;

@Component
public class ControllerProcessor {
    private static final Logger log = LoggerFactory.getLogger(ControllerProcessor.class);

    @Value("${chat.endpoint.controller}")
    private String chatEndpointController;

    private final StackspotClient stackspotClient;
    private final S3UploadClient s3UploadClient;

    public ControllerProcessor(StackspotClient stackspotClient, S3UploadClient s3UploadClient) {
        this.stackspotClient = stackspotClient;
        this.s3UploadClient = s3UploadClient;
    }

    public Mono<ResponseEntity<ApiResponse>> processControllerLogic(
            java.util.List<ControllerMatch> matches, String scope, String path) {

        Optional<ControllerMatch> bothFound = matches.stream()
                .filter(m -> m.scopeFound() && m.pathFound())
                .findFirst();

        if (bothFound.isPresent()) {
            log.info("Endpoint já existe na classe: {}", bothFound.get().className());
            String response = "Já existe o endpoint com o escopo informado.\nClasse: " + bothFound.get().className();
            return Mono.just(ResponseEntity.ok(new ApiResponse(response)));
        }

        Optional<ControllerMatch> scopeOnly = matches.stream()
                .filter(m -> m.scopeFound() && !m.pathFound())
                .findFirst();

        if (scopeOnly.isPresent()) {
            log.info("Encontrado scope existente, processando com IA");
            return processExistingScope(scopeOnly.get(), path);
        }

        log.info("Nenhum match encontrado, criando novo endpoint");
        return createNewEndpoint(scope, path);
    }

    private Mono<ResponseEntity<ApiResponse>> processExistingScope(ControllerMatch match, String path) {
        return stackspotClient.getAccessToken()
                .flatMap(token -> s3UploadClient.uploadFileToEndpoint(match.filePath(), token)
                        .flatMap(uploadId -> stackspotClient.callChatEndpoint(
                                Collections.singletonList(uploadId),
                                "scope: " + match.scope() + ", path: " + path,
                                token, chatEndpointController)
                                .map(response -> ResponseEntity
                                        .ok(new ApiResponse(JsonUtils.extractMessage(response)))))
                        .onErrorResume(error -> {
                            log.error("Erro no processamento com scope existente: {}", error.getMessage());
                            return Mono.just(ResponseEntity.internalServerError()
                                    .body(new ApiResponse("Erro no processamento: " + error.getMessage())));
                        }));
    }

    public Mono<ResponseEntity<ApiResponse>> createNewEndpoint(String scope, String path) {
        return stackspotClient.getAccessToken()
                .flatMap(token -> stackspotClient.callChatEndpoint(
                        Collections.emptyList(),
                        "scope: " + scope + ", path: " + path,
                        token, chatEndpointController)
                        .map(response -> ResponseEntity.ok(new ApiResponse(JsonUtils.extractMessage(response)))))
                .onErrorResume(error -> {
                    log.error("Erro na criação de novo endpoint: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(new ApiResponse("Erro na criação: " + error.getMessage())));
                });
    }

    public Mono<ResponseEntity<ApiResponse>> processDirectMessage(String userMessage) {
        log.info("Processando mensagem direta: {}", userMessage);

        return stackspotClient.getAccessToken()
                .flatMap(token -> stackspotClient.callChatEndpoint(
                        Collections.emptyList(),
                        userMessage,
                        token, chatEndpointController)
                        .map(response -> ResponseEntity.ok(new ApiResponse(JsonUtils.extractMessage(response)))))
                .doOnSuccess(result -> log.info("Processamento direto de mensagem concluído com sucesso"))
                .onErrorResume(error -> {
                    log.error("Erro no processamento direto de mensagem: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(new ApiResponse("Erro no processamento: " + error.getMessage())));
                });
    }
}
