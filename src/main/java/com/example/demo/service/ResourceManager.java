package com.example.demo.service;

import com.example.demo.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.file.Paths;

@Component
public class ResourceManager {
    private static final Logger log = LoggerFactory.getLogger(ResourceManager.class);

    public void cleanupResources(String zipPath) {
        try {
            log.debug("Limpando recursos temporários");
            FileUtils.deleteDirectoryQuietly(Paths.get(zipPath));
            FileUtils.deleteDirectory(new File("repo"));
        } catch (Exception e) {
            log.warn("Erro ao limpar recursos: {}", e.getMessage());
        }
    }

    public Mono<String> extractRepository(String zipPath) {
        return Mono.fromCallable(() -> {
            log.debug("Extraindo repositório: {}", zipPath);
            return FileUtils.unzipFile(zipPath);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
