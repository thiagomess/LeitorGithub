package com.example.demo.client;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Component
public class GithubClient {
    private static final String GITHUB_REPO_ZIP = "https://github.com/thiagomess/resource-service/archive/refs/heads/main.zip";
    private static final String ZIP_FILE = "repo.zip";

    private final WebClient webClient;

    public GithubClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<String> downloadRepoAsZip() {
        Path zipPath = Paths.get(ZIP_FILE);

        return webClient.get()
                .uri(GITHUB_REPO_ZIP)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .as(flux -> DataBufferUtils.write(flux, zipPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING))
                .then(Mono.just(ZIP_FILE));
    }
}
