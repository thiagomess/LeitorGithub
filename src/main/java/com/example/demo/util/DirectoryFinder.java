package com.example.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.enums.DirectoryType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;


public final class DirectoryFinder {
    private static final Logger log = LoggerFactory.getLogger(DirectoryFinder.class);

    private static final String[] POSSIBLE_CONTROLLER_PATHS = {
            "resource-service-main/src/main/java/com/example/demo/controller",
            "src/main/java/com/example/demo/controller",
            "src/main/java"
    };

    private static final String[] POSSIBLE_TEST_PATHS = {
            "src/test/java",
            "test/java",
            "tests",
            "src/test"
    };

    private static Optional<String> findDirectory(String baseDir, DirectoryType type) {
        Path basePath = Paths.get(baseDir);

        String[] possiblePaths = switch (type) {
        case CONTROLLER -> POSSIBLE_CONTROLLER_PATHS;
        case TEST -> POSSIBLE_TEST_PATHS;
        default -> {
            log.warn("Tipo de diretório desconhecido: {}", type);
            yield new String[0];
        	}
        };

        log.debug("Procurando diretório de {} em: {}", type.getDescription(), baseDir);

        for (String possiblePath : possiblePaths) {
            Path fullPath = basePath.resolve(possiblePath);
            if (Files.exists(fullPath) && Files.isDirectory(fullPath)) {
                log.debug("Encontrado diretório de {}: {}", type.getDescription(), fullPath);
                return Optional.of(fullPath.toString());
            }
        }

        log.debug("Nenhum diretório de {} encontrado em: {}", type.getDescription(), baseDir);
        return Optional.empty();
    }

    public static String findControllersDirectory(String extractDir) {
        return findDirectory(extractDir, DirectoryType.CONTROLLER)
                .orElse(extractDir); 
    }

    public static Optional<String> findTestDirectory(String projectRoot) {
        return findDirectory(projectRoot, DirectoryType.TEST);
    }

    public static Optional<Path> findFileInDirectory(String directory, String fileName) {
        Path dirPath = Paths.get(directory);
        log.debug("Procurando arquivo '{}' em: {}", fileName, directory);

        if (!isValidDirectory(dirPath)) {
            return Optional.empty();
        }

        try (Stream<Path> paths = Files.walk(dirPath)) {
            Optional<Path> foundFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst();

            logFileSearchResult(foundFile, fileName, directory);
            return foundFile;
        } catch (IOException e) {
            log.error("Erro ao procurar arquivo: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<Path> findTestClass(String projectRoot, String originalClassName) {
        // Remove a extensão .java se presente
        if (originalClassName.endsWith(".java")) {
            originalClassName = originalClassName.substring(0, originalClassName.length() - 5);
        }

        String testClassName = originalClassName + "Test.java";

        Optional<String> testDir = findTestDirectory(projectRoot);
        if (testDir.isPresent()) {
            return findFileInDirectory(testDir.get(), testClassName);
        }

        return Optional.empty();
    }


    private static boolean isValidDirectory(Path dirPath) {
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            log.debug("Diretório não existe ou não é um diretório válido: {}", dirPath);
            return false;
        }
        return true;
    }


    private static void logFileSearchResult(Optional<Path> foundFile, String fileName, String directory) {
        if (foundFile.isPresent()) {
            log.debug("Arquivo encontrado: {}", foundFile.get());
        } else {
            log.debug("Arquivo '{}' não encontrado em: {}", fileName, directory);
        }
    }


    public static String extractProjectRoot(Path filePath) {
        String pathStr = normalizePath(filePath.toString());
        return findProjectRoot(pathStr);
    }


    public static String findProjectRoot(String pathStr) {
        String projectRoot;

        if (pathStr.contains("/src/")) {
            projectRoot = pathStr.split("/src/")[0];
        }else {
            int lastSlash = pathStr.lastIndexOf("/");
            projectRoot = lastSlash > 0 ? pathStr.substring(0, lastSlash) : pathStr;
        }

        log.debug("Diretório raiz do projeto extraído: {}", projectRoot);
        return projectRoot;
    }

    public static String normalizePath(String path) {
        return path.replace("\\", "/");
    }


}
