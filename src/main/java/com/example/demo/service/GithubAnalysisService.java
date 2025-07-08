package com.example.demo.service;

import com.example.demo.client.GithubClient;
import com.example.demo.client.StackspotClient;
import com.example.demo.client.S3UploadClient;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ControllerMatch;
import com.example.demo.dto.RepoContext;
import com.example.demo.util.DirectoryFinder;
import com.example.demo.util.FileUtils;
import com.example.demo.util.JsonUtils;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class GithubAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalysisService.class);

    private final GithubClient githubClient;
    private final StackspotClient stackspotClient;
    private final S3UploadClient s3UploadClient;
    private final JavaParser javaParser;

    public GithubAnalysisService(GithubClient githubClient, StackspotClient stackspotClient,
            S3UploadClient s3UploadClient) {
        this.githubClient = githubClient;
        this.stackspotClient = stackspotClient;
        this.s3UploadClient = s3UploadClient;
        this.javaParser = new JavaParser();
    }

    public Mono<ResponseEntity<ApiResponse>> analyzeRepository(String scope, String path) {
        log.info("Iniciando análise do repositório - Scope: {}, Path: {}", scope, path);

        return githubClient.downloadRepository()
                .flatMap(zipPath -> processRepository(zipPath, scope, path))
                .doOnSuccess(result -> log.info("Análise do repositório concluída com sucesso"))
                .doOnError(error -> log.error("Erro na análise do repositório: {}", error.getMessage()));
    }

    private Mono<ResponseEntity<ApiResponse>> processRepository(String zipPath, String scope, String path) {
        return Mono.fromCallable(() -> {
            log.debug("Extraindo repositório: {}", zipPath);
            String extractDir = FileUtils.unzipFile(zipPath);

            log.debug("Procurando diretório de controllers");
            String controllersDir = DirectoryFinder.findControllersDirectory(extractDir);

            log.debug("Analisando arquivos Java no diretório: {}", controllersDir);
            List<ControllerMatch> matches = analyzeJavaFiles(controllersDir, scope, path);

            log.info("Encontrados {} matches no repositório", matches.size());
            return new RepoContext(zipPath, extractDir, matches);
        })
                .flatMap(context -> processMatches(context, scope, path))
                .doFinally(signalType -> cleanupResources(zipPath));
    }

    private List<ControllerMatch> analyzeJavaFiles(String directory, String scope, String path) {
        List<ControllerMatch> result = new ArrayList<>();

        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            log.warn("Diretório não existe: {}", directory);
            return result;
        }

        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaPath -> analyzeJavaFile(javaPath, scope, path, result));
        } catch (IOException e) {
            log.error("Erro ao percorrer diretório {}: {}", directory, e.getMessage());
        }

        return result;
    }

    private void analyzeJavaFile(Path javaPath, String scope, String path, List<ControllerMatch> result) {
        boolean scopeFound = false;
        boolean urlFound = false;
        String className = javaPath.getFileName().toString();

        log.debug("Analisando arquivo Java: {}", javaPath);

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(javaPath);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                boolean isController = cu.findAll(AnnotationExpr.class).stream()
                        .anyMatch(a -> a.getNameAsString().equals("RestController") ||
                                a.getNameAsString().equals("Controller"));

                if (isController) {
                    log.debug("Arquivo {} é um controller, analisando métodos", className);
                    for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                        if (hasScope(method, scope)) {
                            scopeFound = true;
                        }
                        if (hasPath(method, path)) {
                            urlFound = true;
                            log.info("Encontrado método com path '{}' em {}: {}", path, className,
                                    method.getNameAsString());
                        }
                    }
                }
            } else {
                log.debug("Falha ao analisar arquivo: {}", javaPath);
            }
        } catch (Exception e) {
            log.warn("Erro ao analisar arquivo {}: {}", javaPath, e.getMessage(), e);
        }

        if (scopeFound || urlFound) {
            result.add(new ControllerMatch(javaPath, className, scopeFound, urlFound, scope));
            log.debug("Match encontrado em {}: scope={}, url={}", className, scopeFound, urlFound);
        }
    }

    private boolean hasScope(MethodDeclaration method, String scope) {
        Optional<AnnotationExpr> preAuthorize = method.getAnnotationByName("PreAuthorize");
        if (!preAuthorize.isPresent()) {
            return false;
        }

        String annotation = preAuthorize.get().toString();
        log.debug("Verificando anotação @PreAuthorize: {}", annotation);

        boolean hasHashScope = annotation.contains("#oauth2.hasScope('" + scope + "')");
        boolean hasNoHashScope = annotation.contains("oauth2.hasScope('" + scope + "')");

        if (hasHashScope || hasNoHashScope) {
            log.info("Encontrado método com scope '{}' em {}: {} (formato: {})",
                    scope,
                    method.getNameAsString(),
                    annotation,
                    hasHashScope ? "com #" : "sem #");
            return true;
        }

        return false;
    }

    private boolean hasPath(MethodDeclaration method, String path) {
        Optional<AnnotationExpr> mapping = method.getAnnotationByName("GetMapping")
                .or(() -> method.getAnnotationByName("PostMapping"))
                .or(() -> method.getAnnotationByName("RequestMapping"));

        if (!mapping.isPresent()) {
            return false;
        }

        String annotation = mapping.get().toString();
        log.debug("Verificando anotação de mapeamento: {}", annotation);

        boolean hasPathAttribute = annotation.contains("path = \"" + path + "\"");
        boolean hasValueAttribute = annotation.contains("value = \"" + path + "\"");
        boolean hasDirectPath = annotation.contains("\"" + path + "\"");

        if (hasPathAttribute || hasValueAttribute || hasDirectPath) {
            log.info("Encontrado método com path '{}' em {}: {}",
                    path,
                    method.getNameAsString(),
                    annotation);
            return true;
        }

        return false;
    }

    private Mono<ResponseEntity<ApiResponse>> processMatches(RepoContext context, String scope, String path) {
        List<ControllerMatch> matches = context.matches();

        Optional<ControllerMatch> bothFound = matches.stream()
                .filter(m -> m.scopeFound() && m.urlFound())
                .findFirst();

        if (bothFound.isPresent()) {
            log.info("Endpoint já existe na classe: {}", bothFound.get().className());
            String response = "Já existe o endpoint com o escopo informado.\nClasse: " + bothFound.get().className();
            return Mono.just(ResponseEntity.ok(new ApiResponse(response)));
        }

        Optional<ControllerMatch> scopeOnly = matches.stream()
                .filter(m -> m.scopeFound() && !m.urlFound())
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
                                "scope: " + match.scope() + ", url: " + path,
                                token)
                                .map(response -> ResponseEntity
                                        .ok(new ApiResponse(JsonUtils.extractMessage(response)))))
                        .onErrorResume(error -> {
                            log.error("Erro no processamento com scope existente: {}", error.getMessage());
                            return Mono.just(ResponseEntity.internalServerError()
                                    .body(new ApiResponse("Erro no processamento: " + error.getMessage())));
                        }));
    }

    private Mono<ResponseEntity<ApiResponse>> createNewEndpoint(String scope, String path) {
        return stackspotClient.getAccessToken()
                .flatMap(token -> stackspotClient.callChatEndpoint(
                        Collections.emptyList(),
                        "scope: " + scope + ", url: " + path,
                        token)
                        .map(response -> ResponseEntity.ok(new ApiResponse(JsonUtils.extractMessage(response)))))
                .onErrorResume(error -> {
                    log.error("Erro na criação de novo endpoint: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(new ApiResponse("Erro na criação: " + error.getMessage())));
                });
    }

    private void cleanupResources(String zipPath) {
        try {
            log.debug("Limpando recursos temporários");
            FileUtils.deleteDirectoryQuietly(Paths.get(zipPath));
            FileUtils.deleteDirectory(new File("repo"));
        } catch (Exception e) {
            log.warn("Erro ao limpar recursos: {}", e.getMessage());
        }
    }


    public Mono<ResponseEntity<ApiResponse>> processDirectMessage(String userMessage) {
        log.info("Processando mensagem direta: {}", userMessage);

        return stackspotClient.getAccessToken()
                .flatMap(token -> stackspotClient.callChatEndpoint(
                        Collections.emptyList(),
                        userMessage,
                        token)
                        .map(response -> ResponseEntity.ok(new ApiResponse(JsonUtils.extractMessage(response)))))
                .doOnSuccess(result -> log.info("Processamento direto de mensagem concluído com sucesso"))
                .onErrorResume(error -> {
                    log.error("Erro no processamento direto de mensagem: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(new ApiResponse("Erro no processamento: " + error.getMessage())));
                });
    }
}
