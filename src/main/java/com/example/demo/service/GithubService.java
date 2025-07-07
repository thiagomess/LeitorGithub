package com.example.demo.service;

import com.example.demo.client.GithubClient;
import com.example.demo.util.FileUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GithubService {

    private final GithubClient githubClient;

    public GithubService(GithubClient githubClient) {
        this.githubClient = githubClient;
    }

    public Mono<String> downloadAndExtractRepo() {
        return githubClient.downloadRepoAsZip()
                .map(zipPath -> {
                    try {
                        String extractDir = FileUtil.unzip(zipPath, "repo");
                        return extractDir;
                    } catch (Exception e) {
                        throw new RuntimeException("Erro ao extrair repositório", e);
                    }
                });
    }
}
