package com.example.demo.service;

import com.example.demo.client.GithubClient;
import com.example.demo.dto.ControllerMatch;
import com.example.demo.dto.RepoContext;
import com.example.demo.util.DirectoryFinder;
import com.example.demo.util.FileUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

@Service
public class RepositoryProcessingService {

    private final GithubClient githubClient;
    private final JavaAnalysisService javaAnalysisService;

    public RepositoryProcessingService(GithubClient githubClient, JavaAnalysisService javaAnalysisService) {
        this.githubClient = githubClient;
        this.javaAnalysisService = javaAnalysisService;
    }

    public Mono<RepoContext> downloadAndAnalyzeRepository(String scope, String path) {
        return githubClient.downloadRepoAsZip()
                .flatMap(zipPath -> Mono.fromCallable(() -> {
                    String extractDir = FileUtils.unzipFile(zipPath);
                    String controllersDir = DirectoryFinder.findControllersDirectory(extractDir);
                    List<ControllerMatch> matches = javaAnalysisService.findControllersWithScopeAndPath(controllersDir,
                            scope, path);
                    return new RepoContext(zipPath, extractDir, matches);
                }));
    }

    public void cleanupRepository(RepoContext context) {
        try {
            FileUtils.deleteDirectoryQuietly(Paths.get(context.zipPath()));
            FileUtils.deleteDirectory(new File(context.extractDir()));
        } catch (Exception e) {
            System.err.println("Erro ao limpar arquivos: " + e.getMessage());
        }
    }
}
