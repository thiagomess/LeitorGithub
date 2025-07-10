package com.example.demo.service;

import com.example.demo.client.GithubClient;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ControllerMatch;
import com.example.demo.dto.RepoContext;
import com.example.demo.enums.TypeAction;
import com.example.demo.util.DirectoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;


@Service
public class GithubAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalysisService.class);

    private final GithubClient githubClient;
    private final JavaSourceAnalyzer javaSourceAnalyzer;
    private final UnitTestProcessor unitTestProcessor;
    private final ControllerProcessor controllerProcessor;
    private final ResourceManager resourceManager;

    public GithubAnalysisService(GithubClient githubClient,
            JavaSourceAnalyzer javaSourceAnalyzer,
            UnitTestProcessor unitTestProcessor,
            ControllerProcessor controllerProcessor,
            ResourceManager resourceManager) {
        this.githubClient = githubClient;
        this.javaSourceAnalyzer = javaSourceAnalyzer;
        this.unitTestProcessor = unitTestProcessor;
        this.controllerProcessor = controllerProcessor;
        this.resourceManager = resourceManager;
    }

 
    public Mono<ResponseEntity<ApiResponse>> analyzeRepository(String scope, String path, String type) {
        log.info("Iniciando análise do repositório - Scope: {}, Path: {}, Type: {}", scope, path, type);

        return githubClient.downloadRepository()
                .flatMap(zipPath -> processRepository(zipPath, scope, path, type))
                .doOnSuccess(result -> log.info("Análise do repositório concluída com sucesso"))
                .doOnError(error -> log.error("Erro na análise do repositório: {}", error.getMessage()));
    }

    /**
     * Processa o repositório para análise.
     */
    private Mono<ResponseEntity<ApiResponse>> processRepository(String zipPath, String scope, String path,
            String type) {
        return resourceManager.extractRepository(zipPath)
                .flatMap(extractDir -> findControllersDirectory(extractDir))
                .flatMap(controllersDir -> analyzeJavaFilesAsync(controllersDir, scope, path, zipPath))
                .flatMap(context -> processMatches(context, scope, path, type))
                .doFinally(signalType -> resourceManager.cleanupResources(zipPath));
    }

    /**
     * Encontra o diretório de controllers no repositório extraído.
     */
    private Mono<String> findControllersDirectory(String extractDir) {
        return Mono.fromCallable(() -> {
            log.debug("Procurando diretório de controllers");
            return DirectoryFinder.findControllersDirectory(extractDir);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Analisa arquivos Java de forma assíncrona.
     */
    private Mono<RepoContext> analyzeJavaFilesAsync(String controllersDir, String scope, String path, String zipPath) {
        return Mono.fromCallable(() -> {
            log.debug("Analisando arquivos Java no diretório: {}", controllersDir);
            List<ControllerMatch> matches = javaSourceAnalyzer.analyzeJavaFiles(controllersDir, scope, path);
            log.info("Encontrados {} matches no repositório", matches.size());
            return new RepoContext(zipPath, controllersDir, matches);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Define a ação a ser tomada com base no tipo de processamento.
     */
    private Mono<ResponseEntity<ApiResponse>> processMatches(RepoContext context, String scope, String path,
            String type) {
        log.info("Processando matches com type: {}", type);

        try {
            TypeAction typeAction = TypeAction.fromString(type);

            if (typeAction == TypeAction.UNIT_TEST) {
                Optional<ControllerMatch> scopeFound = context.matches().stream()
                        .filter(m -> m.scopeFound())
                        .findFirst();
                return unitTestProcessor.processUnitTest(scopeFound.orElse(null), scope, path);
            } else {
                return controllerProcessor.processControllerLogic(context.matches(), scope, path);
            }
        } catch (UnsupportedOperationException e) {
            log.warn("Tipo não suportado: {}, usando lógica padrão de controller", type);
            return controllerProcessor.processControllerLogic(context.matches(), scope, path);
        }
    }

    /**
     * Processa uma mensagem direta para o chatbot.
     */
    public Mono<ResponseEntity<ApiResponse>> processDirectMessage(String userMessage) {
        return controllerProcessor.processDirectMessage(userMessage);
    }
}
