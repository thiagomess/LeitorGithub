package com.example.demo.service;

import com.example.demo.client.ChatClient;
import com.example.demo.client.OAuth2Client;
import com.example.demo.client.S3UploadClient;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ControllerMatch;
import com.example.demo.dto.RepoContext;
import com.example.demo.util.JsonUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class GithubAnalyzerService {

    private final S3UploadClient s3UploadClient;
    private final ChatClient chatClient;
    private final OAuth2Client oauth2Client;
    private final RepositoryProcessingService repositoryProcessingService;

    public GithubAnalyzerService(
            S3UploadClient s3UploadClient,
            ChatClient chatClient,
            OAuth2Client oauth2Client,
            RepositoryProcessingService repositoryProcessingService) {
        this.s3UploadClient = s3UploadClient;
        this.chatClient = chatClient;
        this.oauth2Client = oauth2Client;
        this.repositoryProcessingService = repositoryProcessingService;
    }

    public Mono<ResponseEntity<ApiResponse>> processAnalysis(String scope, String path) {
        return repositoryProcessingService.downloadAndAnalyzeRepository(scope, path)
                .flatMap(context -> processMatches(context, scope, path)
                        .doFinally(signalType -> repositoryProcessingService.cleanupRepository(context)));
    }

    private Mono<ResponseEntity<ApiResponse>> processMatches(RepoContext context, String scope, String path) {
        List<ControllerMatch> matches = context.matches();

        Optional<ControllerMatch> bothFound = findMatchWithBothScopeAndUrl(matches);
        if (bothFound.isPresent()) {
            String response = "Já existe o endpoint com o escopo informado.\nClasse: " + bothFound.get().className();
            return Mono.just(ResponseEntity.ok(new ApiResponse(response)));
        }

        Optional<ControllerMatch> scopeOnly = findMatchWithScopeOnly(matches);
        if (scopeOnly.isPresent()) {
            return processMatchWithScope(scopeOnly.get(), path);
        }

        return processNewEndpoint(scope, path);
    }

    private Optional<ControllerMatch> findMatchWithBothScopeAndUrl(List<ControllerMatch> matches) {
        return matches.stream()
                .filter(m -> m.scopeFound() && m.urlFound())
                .findFirst();
    }

    private Optional<ControllerMatch> findMatchWithScopeOnly(List<ControllerMatch> matches) {
        return matches.stream()
                .filter(m -> m.scopeFound() && !m.urlFound())
                .findFirst();
    }

    private Mono<ResponseEntity<ApiResponse>> processMatchWithScope(ControllerMatch match, String path) {
        return oauth2Client.getAccessToken()
                .flatMap(token -> s3UploadClient.uploadFileToEndpoint(match.filePath(), token)
                        .flatMap(uploadId -> chatClient.callChatEndpoint(
                                Collections.singletonList(uploadId),
                                "scope: " + match.scope() + ", url: " + path,
                                token)
                                .map(response -> ResponseEntity
                                        .ok(new ApiResponse(JsonUtils.extractMessage(response)))))
                        .onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
                                .body(new ApiResponse("Erro no upload do arquivo: " + error.getMessage())))));
    }

    private Mono<ResponseEntity<ApiResponse>> processNewEndpoint(String scope, String path) {
        return oauth2Client.getAccessToken()
                .flatMap(token -> chatClient.callChatEndpoint(
                        Collections.emptyList(),
                        "scope: " + scope + ", url: " + path,
                        token)
                        .map(response -> ResponseEntity.ok(new ApiResponse(JsonUtils.extractMessage(response)))));
    }
}
