package com.example.demo.service;

import com.example.demo.constants.ApplicationConstants;
import com.example.demo.util.DirectoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public Mono<String> findTestDirectory(String projectRoot) {
        return Mono.fromCallable(() -> {
            // Utiliza o DirectoryFinder para localizar o diretório de testes
            Optional<String> testDirOpt = DirectoryFinder.findTestDirectory(projectRoot);

            if (testDirOpt.isPresent()) {
                return testDirOpt.get();
            }
            log.warn("Nenhum diretório de teste encontrado em: {}", projectRoot);
            return projectRoot + ApplicationConstants.DEFAULT_TEST_PATH;
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

    /**
     * Encontra a classe original que corresponde a uma classe de teste.
     */
    public Path findOriginalClass(Path testClassPath, String originalClassName) {
        log.debug("Procurando classe original: {}", originalClassName);

        try {
            // Extrair o diretório raiz do projeto usando o DirectoryFinder
            String projectRoot = DirectoryFinder.extractProjectRoot(testClassPath);

            // Usar o método específico do DirectoryFinder para encontrar classes Java
            Optional<Path> foundClass = DirectoryFinder.findJavaClass(projectRoot, originalClassName);

            if (foundClass.isPresent()) {
                log.info("Classe original encontrada: {}", foundClass.get());
                return foundClass.get();
            }

            log.warn("Classe original não encontrada em diretórios padrão");

        } catch (Exception e) {
            log.error("Erro ao buscar classe original: {}", e.getMessage());
        }

        return testClassPath;
    }
}
