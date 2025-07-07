package com.example.demo.service;

import com.example.demo.client.ChatClient;
import com.example.demo.client.GithubClient;
import com.example.demo.client.OAuth2Client;
import com.example.demo.client.S3UploadClient;
import com.example.demo.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class GithubAnalyzerService {

    private static final String CONTROLLERS_PATH = "resource-service-main/src/main/java/com/example/demo/controller";

    private final GithubClient githubClient;
    private final S3UploadClient s3UploadClient;
    private final ChatClient chatClient;
    private final OAuth2Client oauth2Client;

    public GithubAnalyzerService(
            GithubClient githubClient,
            S3UploadClient s3UploadClient,
            ChatClient chatClient,
            OAuth2Client oauth2Client) {
        this.githubClient = githubClient;
        this.s3UploadClient = s3UploadClient;
        this.chatClient = chatClient;
        this.oauth2Client = oauth2Client;
    }

    public Mono<ResponseEntity<ApiResponse>> processAnalysis(String scope, String path) {
        return Mono.fromCallable(() -> {
            String zipPath = githubClient.downloadRepoAsZip();
            String extractDir = unzip(zipPath);
            String controllersDir = extractDir + "/" + CONTROLLERS_PATH;
            List<ControllerMatch> matches = findControllersWithScopeAndPath(controllersDir, scope, path);
            return new RepoContext(zipPath, extractDir, matches);
        })
                .flatMap(ctx -> processMatches(ctx, scope, path)
                        .doFinally(signalType -> {
                            try {
                                Files.deleteIfExists(Paths.get(ctx.zipPath));
                                deleteDirectory(new File(ctx.extractDir));
                            } catch (IOException ignored) {
                            }
                        }));
    }

    private Mono<ResponseEntity<ApiResponse>> processMatches(RepoContext ctx, String scope, String path) {
        List<ControllerMatch> matches = ctx.matches;

        Optional<ControllerMatch> bothFound = matches.stream()
                .filter(m -> m.scopeFound && m.urlFound)
                .findFirst();
        if (bothFound.isPresent()) {
            String resp = "Já existe o endpoint com o escopo informado.\nClasse: " + bothFound.get().className;
            return Mono.just(ResponseEntity.ok(new ApiResponse(resp)));
        }

        Optional<ControllerMatch> scopeOnly = matches.stream()
                .filter(m -> m.scopeFound && !m.urlFound)
                .findFirst();
        if (scopeOnly.isPresent()) {
            ControllerMatch match = scopeOnly.get();            return oauth2Client.getAccessToken()
                .flatMap(token -> 
                    s3UploadClient.uploadFileToEndpoint(match.filePath, token)
                        .flatMap(uploadId -> 
                            chatClient.callChatEndpoint(
                                Collections.singletonList(uploadId),
                                "scope: " + scopeOnly.get().scope + ", url: " + path,
                                token)
                            .map(iaResp -> ResponseEntity.ok(new ApiResponse(extractMessageFromJson(iaResp))))
                        )
                        .onErrorResume(e -> 
                            Mono.just(ResponseEntity.internalServerError()
                                .body(new ApiResponse("Erro no upload do arquivo: " + e.getMessage())))
                        )
                );
        }

        return oauth2Client.getAccessToken()
                .flatMap(token -> 
                    chatClient.callChatEndpoint(
                            Collections.emptyList(),
                            "scope: " + scope + ", url: " + path,
                            token)
                        .map(iaResp -> ResponseEntity.ok(new ApiResponse(extractMessageFromJson(iaResp))))
                );
    }

    // --- Métodos locais que NÃO fazem chamadas HTTP ---

    private String unzip(String zipPath) throws IOException {
        String destDir = "repo";
        try (var zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipPath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return destDir;
    }

    private List<ControllerMatch> findControllersWithScopeAndPath(String dir, String scope, String path)
            throws IOException {
        List<ControllerMatch> result = new ArrayList<>();
        JavaParser parser = new JavaParser();
        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaPath -> {
                        boolean scopeFound = false;
                        boolean urlFound = false;
                        String className = javaPath.getFileName().toString();
                        try {
                            ParseResult<CompilationUnit> parseResult = parser.parse(javaPath);
                            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                                CompilationUnit cu = parseResult.getResult().get();
                                for (var method : cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
                                    Optional<com.github.javaparser.ast.expr.AnnotationExpr> preAuthorize = method
                                            .getAnnotationByName("PreAuthorize");
                                    Optional<com.github.javaparser.ast.expr.AnnotationExpr> getMapping = method
                                            .getAnnotationByName("GetMapping")
                                            .or(() -> method.getAnnotationByName("RequestMapping"));

                                    if (preAuthorize.isPresent() && preAuthorize.get().toString()
                                            .contains("#oauth2.hasScope('" + scope + "')")) {
                                        scopeFound = true;
                                    }
                                    if (getMapping.isPresent()
                                            && (getMapping.get().toString().contains("path = \"" + path + "\"") ||
                                                    getMapping.get().toString().contains("\"" + path + "\""))) {
                                        urlFound = true;
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        if (scopeFound || urlFound) {
                            result.add(new ControllerMatch(javaPath, className, scopeFound, urlFound, scope));
                        }
                    });
        }
        return result;
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            for (File sub : Objects.requireNonNull(file.listFiles())) {
                deleteDirectory(sub);
            }
        }
        file.delete();
    }

    private String extractMessageFromJson(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            return obj.optString("message", json);
        } catch (Exception e) {
            return json;
        }
    }

    // --- DTOs internos (pode mover para pacote dto) ---

    private static class RepoContext {
        String zipPath;
        String extractDir;
        List<ControllerMatch> matches;

        RepoContext(String zipPath, String extractDir, List<ControllerMatch> matches) {
            this.zipPath = zipPath;
            this.extractDir = extractDir;
            this.matches = matches;
        }
    }

    private static class ControllerMatch {
        Path filePath;
        String className;
        boolean scopeFound;
        boolean urlFound;
        String scope;

        ControllerMatch(Path filePath, String className, boolean scopeFound, boolean urlFound, String scope) {
            this.filePath = filePath;
            this.className = className;
            this.scopeFound = scopeFound;
            this.urlFound = urlFound;
            this.scope = scope;
        }
    }
}
