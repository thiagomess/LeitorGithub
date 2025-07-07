package com.example.demo.service;

import com.example.demo.client.ChatClient;
import com.example.demo.client.GithubClient;
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

    private static final String CONTROLLERS_PATH = "modules-master/rest-oauth2/src/main/java/ru/phi/modules/rest/";
    private static final String FIXED_JWT = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjY1YzUyNmQ2LWExOGQtNDI2NC04MTc1LTlmNWI0NjQ1MjljZiIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50X2lkX3YyIjoiMDFKWTVSRVpCMzRTVlQwR1owRUVZWFgwMTIiLCJhY2NvdW50X25hbWUiOiJUaGlhZ28gZ29tZXMiLCJhY2NvdW50X3NsdWciOiIzZDE5ZTgtdGhpYWdvLWdvbWVzIiwiYWNjb3VudF90eXBlIjoiRlJFRU1JVU0iLCJhdHRyaWJ1dGVzIjp7fSwiYXVkIjpbImZmMTUwNTI3LTE4MDctNDExMi1hNjUzLTY2ZTJiMTcwNWIxOSJdLCJhenAiOiJiMDI1N2ZmZC02MGYwLTQxYWItOTJhOC0wNGM3ZDVlZTQzZDQiLCJjbGllbnRJZCI6ImZmMTUwNTI3LTE4MDctNDExMi1hNjUzLTY2ZTJiMTcwNWIxOSIsImNsaWVudF9pZCI6ImZmMTUwNTI3LTE4MDctNDExMi1hNjUzLTY2ZTJiMTcwNWIxOSIsImVtYWlsIjoidGhpYWdvZ29tZXMxOUBob3RtYWlsLmNvbSIsImV4cCI6MTc1MTg1NDY1NSwiZmFtaWx5X25hbWUiOiJHb21lcyIsImdpdmVuX25hbWUiOiJUaGlhZ28gZ29tZXMiLCJpYXQiOjE3NTE4NTM0NTUsImlzcyI6Imh0dHBzOi8vYXV0aC5zdGFja3Nwb3QuY29tL3N0YWNrc3BvdC1mcmVlbWl1bS9vaWRjIiwianRpIjoiak44cnlJT3RZMnc4Z2sySmFkOHdiZms2Q3dBbHNHajd2cE5UVW96YmV3QTN3U3J0MVhpTlJvakdxbUpJNmhVVyIsIm5hbWUiOiJUaGlhZ28gZ29tZXMgR29tZXMiLCJuYmYiOjE3NTE4NTM0NTUsInByZWZlcnJlZF91c2VybmFtZSI6InRoaWFnb2dvbWVzMTlAaG90bWFpbC5jb20iLCJyZWFsbSI6InN0YWNrc3BvdC1mcmVlbWl1bSIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJkZXZlbG9wZXIiLCJhaV9hZG1pbiIsIjAxSDNBWEtLWlNFSDFaVE43NjI0MVJGQ0gyIiwiYWNjb3VudF9hZG1pbiIsImRlZmF1bHQgdXNlciByb2xlOiA4OTEzNmQ0Ny0zMzE5LTQzMWYtYmNmMi00ZmQ5YTg3ZDA4ZDEiXX0sInJvbGVzIjpbImRldmVsb3BlciIsImFpX2FkbWluIiwiMDFIM0FYS0taU0VIMVpUTjc2MjQxUkZDSDIiLCJhY2NvdW50X2FkbWluIiwiZGVmYXVsdCB1c2VyIHJvbGU6IDg5MTM2ZDQ3LTMzMTktNDMxZi1iY2YyLTRmZDlhODdkMDhkMSJdLCJzY29wZSI6ImF0dHJpYnV0ZXMgcm9sZXMgZW1haWwiLCJzdWIiOiJmZjE1MDUyNy0xODA3LTQxMTItYTY1My02NmUyYjE3MDViMTkiLCJ0ZW5hbnQiOiJzdGFja3Nwb3QtZnJlZW1pdW0iLCJ0ZW5hbnRfaWQiOiJiMDI1N2ZmZC02MGYwLTQxYWItOTJhOC0wNGM3ZDVlZTQzZDQiLCJ0b2tlblR5cGUiOiJDTElFTlRfU0VSVklDRV9BQ0NPVU5UIiwidG9rZW5fdHlwZSI6IkNMSUVOVF9QRVJTT05BTCIsInRyaWFsX2FjY291bnRfc3RhdHVzIjoiUkVHSVNURVJFRCIsInVzZXJfaWQiOiI4OTEzNmQ0Ny0zMzE5LTQzMWYtYmNmMi00ZmQ5YTg3ZDA4ZDEiLCJ1c2VybmFtZSI6InRoaWFnb2dvbWVzMTlAaG90bWFpbC5jb20ifQ.NJsZMIfjNjC49VCPoQ0QuPNLNwfyGKZP38lVlFg7BI-lMrKcgiCbqjps0-Owfii1dgMMD2pgIed8RLi47D50UdK-kvFB6v4p9WV4BOSHJBxX12NOvTFTsKKdYnQyi00HUVgFi1gx0TDoFeDpyfD8Ee52nLQxwnhczVNejMBnwsLSPxV76e56nZ3rnLeLO7j5v-4PAkuUo1tCl6r3bbh7_wxmj7l-Yh_BwON0RAZXFpWFIIttmTwNU6qT18mDf8gw0V0dPv6epjrJsavmLou1PR1MrYOxI7h0grMa99vDVgmMKYT4xjzNoZhNv1LYWNvzmh6dWTRuxVhy-O1rQO4B4H3XAaImBWzWGmXL8z2TNaPAC1omGXRYW_HPrHprXfr0egi2J4OaOg-8GXyEZ-k0KCK5laE_19m3Ig5EYL-yA9UgAcCara6YoLRXBX2wMyIZSWdS1QAU-FE6K47t7Jzhtj-vlUSlBCLxh-QzmNd--ul6xICL2k7z7WxVUBYNC25kMKm-QTA5f8A33UoHsx6-Lm-1quDlJ2MeFE3cgith7S1FfCUNfuTg9ThUF7_noPCq2qFKzFXJuQF82Vj5X2rUa-j2eKfkr2RoDNWUyA0BhgmgotVISjzVqxNFwbavrhcSoc4wh2nnTef9NyM1PrvNJplqcFr9goL3R2yoeMMWj9c";


    private final GithubClient githubClient;
    private final S3UploadClient s3UploadClient;
    private final ChatClient chatClient;

    public GithubAnalyzerService(
            GithubClient githubClient,
            S3UploadClient s3UploadClient,
            ChatClient chatClient
    ) {
        this.githubClient = githubClient;
        this.s3UploadClient = s3UploadClient;
        this.chatClient = chatClient;
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
                } catch (IOException ignored) {}
            })
        );
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
            ControllerMatch match = scopeOnly.get();
            String uploadId;
            try {
                uploadId = s3UploadClient.uploadFileToEndpoint(match.filePath, FIXED_JWT);
            } catch (Exception e) {
                return Mono.just(ResponseEntity.internalServerError()
                        .body(new ApiResponse("Erro no upload do arquivo: " + e.getMessage())));
            }
            String iaResp = chatClient.callChatEndpoint(
                    Collections.singletonList(uploadId),
                    "scope: " + scopeOnly.get().scope + ", url: " + path,
                    FIXED_JWT
            );
            return Mono.just(ResponseEntity.ok(new ApiResponse(extractMessageFromJson(iaResp))));
        }

        String iaResp = chatClient.callChatEndpoint(
                Collections.emptyList(),
                "scope: " + scope + ", url: " + path,
                FIXED_JWT
        );
        return Mono.just(ResponseEntity.ok(new ApiResponse(extractMessageFromJson(iaResp))));
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

    private List<ControllerMatch> findControllersWithScopeAndPath(String dir, String scope, String path) throws IOException {
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
                                Optional<com.github.javaparser.ast.expr.AnnotationExpr> preAuthorize =
                                        method.getAnnotationByName("PreAuthorize");
                                Optional<com.github.javaparser.ast.expr.AnnotationExpr> getMapping =
                                        method.getAnnotationByName("GetMapping")
                                        .or(() -> method.getAnnotationByName("RequestMapping"));

                                if (preAuthorize.isPresent() && preAuthorize.get().toString().contains("#oauth2.hasScope('" + scope + "')")) {
                                    scopeFound = true;
                                }
                                if (getMapping.isPresent() && (
                                        getMapping.get().toString().contains("path = \"" + path + "\"") ||
                                        getMapping.get().toString().contains("\"" + path + "\""))) {
                                    urlFound = true;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
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
