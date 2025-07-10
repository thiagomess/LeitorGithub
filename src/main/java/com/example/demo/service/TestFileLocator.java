package com.example.demo.service;

import com.example.demo.util.DirectoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Componente responsável por localizar arquivos de teste e classes originais.
 */
@Component
public class TestFileLocator {
    private static final Logger log = LoggerFactory.getLogger(TestFileLocator.class);

    @Value("${default.test.path}")
    private String defaultTestPath;

    public Mono<String> findTestDirectory(String projectRoot) {
        return Mono.fromCallable(() -> {
            // Utiliza o DirectoryFinder para localizar o diretório de testes
            Optional<String> testDirOpt = DirectoryFinder.findTestDirectory(projectRoot);

            if (testDirOpt.isPresent()) {
                return testDirOpt.get();
            }
            log.warn("Nenhum diretório de teste encontrado em: {}", projectRoot);
            return projectRoot + defaultTestPath;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Optional<Path>> findTestForClass(Path originalClassPath, String className) {
        String projectRoot = DirectoryFinder.extractProjectRoot(originalClassPath);
        log.debug("Project root identificado: {}", projectRoot);

        return findTestDirectory(projectRoot)
                .flatMap(testDir -> checkTestClassExists(testDir, className));
    }

    private Mono<Optional<Path>> checkTestClassExists(String testDir, String className) {
        return Mono.fromCallable(() -> {
            log.debug("Procurando classe de teste para: {}", className);

            String projectRoot = DirectoryFinder.extractProjectRoot(Paths.get(testDir));

            Optional<Path> testClassPath = DirectoryFinder.findTestClass(projectRoot, className);

            if (testClassPath.isPresent()) {
                log.info("Classe de teste encontrada: {}", testClassPath.get());
            } else {
                log.debug("Classe de teste não encontrada para: {}", className);
            }

            return testClassPath;
        }).subscribeOn(Schedulers.boundedElastic());
    }

}
