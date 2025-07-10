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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Componente responsável por processar classes de teste unitário.
 * Lida com a criação, atualização e análise de testes unitários.
 */
@Component
public class UnitTestProcessor {
    private static final Logger log = LoggerFactory.getLogger(UnitTestProcessor.class);

    @Value("${chat.endpoint:https://genai-inference-app.stackspot.com/v1/agent/01JY5RF8WWG0V3H32KMNTDGF25/chat}")
    private String chatEndpointTestUnit;

    private final StackspotClient stackspotClient;
    private final S3UploadClient s3UploadClient;
    private final TestFileLocator testFileLocator;

    public UnitTestProcessor(StackspotClient stackspotClient, S3UploadClient s3UploadClient,
            TestFileLocator testFileLocator) {
        this.stackspotClient = stackspotClient;
        this.s3UploadClient = s3UploadClient;
        this.testFileLocator = testFileLocator;
    }

    /**
     * Processa a lógica de teste unitário para um match específico.
     */
    public Mono<ResponseEntity<ApiResponse>> processUnitTest(ControllerMatch match, String scope, String path) {
        if (match == null) {
            log.info("Nenhuma classe com scope encontrada para criar teste unitário");
            return createNewUnitTest(scope, path, "UnknownClass", null);
        }

        String className = match.className().replace(".java", "");
        log.info("Classe com scope encontrada: {}", className);
        Path originalClassPath = match.filePath();

        // Obter a pasta raiz e localizar o teste correspondente
        return testFileLocator.findTestForClass(originalClassPath, className)
                .flatMap(testClassPath -> {
                    if (testClassPath.isPresent()) {
                        log.info("Classe de teste encontrada: {}", testClassPath.get());
                        return processExistingTestClass(testClassPath.get(), originalClassPath, scope, path);
                    } else {
                        log.info("Classe de teste não encontrada, criando teste para: {}", className);
                        return createNewUnitTest(scope, path, className, originalClassPath);
                    }
                });
    }

    /**
     * Processa uma classe de teste existente.
     */
    private Mono<ResponseEntity<ApiResponse>> processExistingTestClass(Path testClassPath, Path originalClassPath,
            String scope, String path) {
        return stackspotClient.getAccessToken()
                .flatMap(token -> {
                    // Upload da classe de teste
                    Mono<String> testClassUpload = s3UploadClient.uploadFileToEndpoint(testClassPath, token);

                    // Upload da classe Java original
                    Mono<String> originalClassUpload = originalClassPath != null
                            ? s3UploadClient.uploadFileToEndpoint(originalClassPath, token)
                            : Mono.just("no-original-class");

                    // Combinar os dois uploads
                    return Mono.zip(testClassUpload, originalClassUpload)
                            .flatMap(uploads -> {
                                String testUploadId = uploads.getT1();
                                String originalUploadId = uploads.getT2();

                                List<String> uploadIds = new ArrayList<>();
                                uploadIds.add(testUploadId);
                                if (!"no-original-class".equals(originalUploadId)) {
                                    uploadIds.add(originalUploadId);
                                    log.info("Enviando classe original e de teste: {} e {}",
                                            originalClassPath.getFileName(), testClassPath.getFileName());
                                } else {
                                    log.warn("Enviando apenas classe de teste: {}", testClassPath.getFileName());
                                }

                                return stackspotClient.callChatEndpoint(
                                        uploadIds,
                                        "scope: " + scope + ", path: " + path + " - Gerar teste unitário",
                                        token, chatEndpointTestUnit)
                                        .map(response -> ResponseEntity
                                                .ok(new ApiResponse(JsonUtils.extractMessage(response))));
                            });
                })
                .onErrorResume(error -> {
                    log.error("Erro no processamento da classe de teste existente: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(new ApiResponse("Erro no processamento da classe de teste: " + error.getMessage())));
                });
    }

    /**
     * Cria um novo teste unitário.
     */
    private Mono<ResponseEntity<ApiResponse>> createNewUnitTest(String scope, String path, String className,
            Path originalClassPath) {
        return stackspotClient.getAccessToken()
                .flatMap(token -> {
                    // Se temos uma classe original, fazemos o upload
                    if (originalClassPath != null) {
                        return s3UploadClient.uploadFileToEndpoint(originalClassPath, token)
                                .flatMap(uploadId -> stackspotClient.callChatEndpoint(
                                        Collections.singletonList(uploadId),
                                        "scope: " + scope + ", path: " + path + ", className: " + className
                                                + " - Criar novo teste unitário",
                                        token, chatEndpointTestUnit)
                                        .map(response -> ResponseEntity
                                                .ok(new ApiResponse(JsonUtils.extractMessage(response)))));
                    } else {
                        // Sem classe original, apenas chamamos a API
                        return stackspotClient.callChatEndpoint(
                                Collections.emptyList(),
                                "scope: " + scope + ", path: " + path + ", className: " + className
                                        + " - Criar novo teste unitário",
                                token, chatEndpointTestUnit)
                                .map(response -> ResponseEntity
                                        .ok(new ApiResponse(JsonUtils.extractMessage(response))));
                    }
                })
                .onErrorResume(error -> {
                    log.error("Erro na criação de novo teste unitário: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(new ApiResponse("Erro na criação do teste: " + error.getMessage())));
                });
    }
}
