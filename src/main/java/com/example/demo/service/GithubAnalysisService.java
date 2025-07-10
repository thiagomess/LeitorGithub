package com.example.demo.service;

import com.example.demo.client.GithubClient;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ControllerMatch;
import com.example.demo.dto.RepoContext;
import com.example.demo.factory.ProcessorFactory;
import com.example.demo.util.DirectoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
public class GithubAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalysisService.class);

    private final GithubClient githubClient;
    private final JavaSourceAnalyzer javaSourceAnalyzer;
    private final ControllerProcessor controllerProcessor;
    private final ResourceManager resourceManager;
    private final ProcessorFactory processorFactory;

    public GithubAnalysisService(GithubClient githubClient,
            JavaSourceAnalyzer javaSourceAnalyzer,
            ControllerProcessor controllerProcessor,
            ResourceManager resourceManager,
            ProcessorFactory processorFactory) {
        this.githubClient = githubClient;
        this.javaSourceAnalyzer = javaSourceAnalyzer;
        this.controllerProcessor = controllerProcessor;
        this.resourceManager = resourceManager;
        this.processorFactory = processorFactory;
    }

    public Mono<ResponseEntity<ApiResponse>> analyzeRepository(String scope, String path, String type) {
        log.info("Iniciando análise do repositório - Scope: {}, Path: {}, Type: {}", scope, path, type);

        return githubClient.downloadRepository()
                .flatMap(zipPath -> processRepository(zipPath, scope, path, type))
                .doOnSuccess(result -> log.info("Análise do repositório concluída com sucesso"))
                .doOnError(error -> log.error("Erro na análise do repositório: {}", error.getMessage()));
    }

    private Mono<ResponseEntity<ApiResponse>> processRepository(String zipPath, String scope, String path,
            String type) {
        return resourceManager.extractRepository(zipPath)
                .flatMap(extractDir -> findControllersDirectory(extractDir))
                .flatMap(controllersDir -> analyzeJavaFilesAsync(controllersDir, scope, path, zipPath))
                .flatMap(context -> processMatches(context, scope, path, type))
                .doFinally(signalType -> resourceManager.cleanupResources(zipPath));
    }

    private Mono<String> findControllersDirectory(String extractDir) {
        return Mono.fromCallable(() -> {
            log.debug("Procurando diretório de controllers");
            return DirectoryFinder.findControllersDirectory(extractDir);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<RepoContext> analyzeJavaFilesAsync(String controllersDir, String scope, String path, String zipPath) {
        return Mono.fromCallable(() -> {
            log.debug("Analisando arquivos Java no diretório: {}", controllersDir);
            List<ControllerMatch> matches = javaSourceAnalyzer.analyzeJavaFiles(controllersDir, scope, path);
            log.info("Encontrados {} matches no repositório", matches.size());
            return new RepoContext(zipPath, controllersDir, matches);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ResponseEntity<ApiResponse>> processMatches(RepoContext context, String scope, String path,
            String type) {
        log.info("Processando matches com type: {}", type);

        return processorFactory.getProcessor(type)
                .process(context.matches(), scope, path);
    }

    public Mono<ResponseEntity<ApiResponse>> processDirectMessage(String userMessage) {
        return controllerProcessor.processDirectMessage(userMessage);
    }
}
