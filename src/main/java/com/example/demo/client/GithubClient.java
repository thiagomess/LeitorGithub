package com.example.demo.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Component
public class GithubClient {

    private static final Logger log = LoggerFactory.getLogger(GithubClient.class);
    private static final int MAX_MEMORY_SIZE = 16 * 1024 * 1024; // 16MB
    private static final int TIMEOUT_SECONDS = 60;
    private static final String DEFAULT_USER_AGENT = "Spring WebClient";

    private final WebClient webClient;

    // GitHub Configuration
    @Value("${github.repo.url:https://github.com/thiagomess/resource-service/archive/refs/heads/main.zip}")
    private String githubRepoUrl;

    @Value("${github.download.path:repo.zip}")
    private String downloadPath;

    public GithubClient(WebClient.Builder webClientBuilder) {
        // Configurando o WebClient para um tamanho máximo de 16MB e timeout de 60s
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                .build();

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true) // Configurar para seguir redirecionamentos automaticamente
                .responseTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .filter(logRequest())
                .build();
    }

    private Path getDefaultDownloadPath() {
        return Paths.get(downloadPath != null ? downloadPath : "repo.zip");
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    public Mono<String> downloadRepository() {
        return downloadRepository(this.githubRepoUrl, getDefaultDownloadPath().toString());
    }

    public Mono<String> downloadRepository(String repoUrl) {
        return downloadRepository(repoUrl, getDefaultDownloadPath().toString());
    }

    public Mono<String> downloadRepository(String repoUrl, String destinationPath) {
        log.info("Iniciando download do repositório: {}", repoUrl);
        final Path zipPath = Paths.get(destinationPath);

        return webClient.get()
                .uri(repoUrl)
                .header(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                .header(HttpHeaders.ACCEPT, "application/zip, application/octet-stream, */*")
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> {
                            log.error("Erro HTTP ao baixar repositório: {}", response.statusCode());
                            return Mono.error(new RuntimeException("Erro HTTP: " + response.statusCode()));
                        })
                .bodyToMono(byte[].class)
                .flatMap(bytes -> saveZipFile(bytes, zipPath))
                .doOnSuccess(path -> log.info("Download concluído com sucesso: {}", path))
                .doOnError(e -> log.error("Erro ao baixar repositório: {}", e.getMessage()))
                .onErrorMap(e -> new RuntimeException("Falha ao baixar repositório: " + e.getMessage(), e));
    }

    private Mono<String> saveZipFile(byte[] bytes, Path zipPath) {
        return Mono.fromCallable(() -> {
            if (bytes == null || bytes.length == 0) {
                throw new RuntimeException("Nenhum dado recebido do servidor");
            }

            log.info("Dados recebidos: {} bytes", bytes.length);

            try {
                // Garantir que o diretório pai existe
                Path parentDir = zipPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }

                // Deletar arquivo anterior se existir
                Files.deleteIfExists(zipPath);

                // Escrever bytes no arquivo
                Files.write(zipPath, bytes);

                long fileSize = Files.size(zipPath);
                log.info("Arquivo salvo com sucesso: {} ({} bytes)", zipPath, fileSize);

                return zipPath.toString();
            } catch (IOException e) {
                log.error("Erro ao salvar arquivo ZIP em {}: {}", zipPath, e.getMessage());
                throw new RuntimeException("Falha ao salvar arquivo ZIP em " + zipPath, e);
            }
        });
    }
}