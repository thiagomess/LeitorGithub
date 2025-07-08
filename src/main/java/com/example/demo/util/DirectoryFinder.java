package com.example.demo.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class DirectoryFinder {

    private static final String[] POSSIBLE_CONTROLLER_PATHS = {
            "resource-service-main/src/main/java/com/example/demo/controller",
            "src/main/java/com/example/demo/controller",
            "main/src/main/java/com/example/demo/controller",
            "resource-service-main/src/main/java/controller",
            "src/main/java/controller"
    };

    private DirectoryFinder() {
        // Utility class
    }

    public static String findControllersDirectory(String extractDir) {
        Path baseDir = Paths.get(extractDir);

        // Primeiro, tenta os caminhos conhecidos
        for (String possiblePath : POSSIBLE_CONTROLLER_PATHS) {
            Path fullPath = baseDir.resolve(possiblePath);
            if (Files.exists(fullPath) && Files.isDirectory(fullPath)) {
                System.out.println("Encontrado diretório de controllers: " + fullPath);
                return fullPath.toString();
            }
        }

        // Se não encontrou, procura qualquer diretório chamado "controller"
        try {
            Optional<Path> controllerDir = Files.walk(baseDir)
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equals("controller"))
                    .findFirst();

            if (controllerDir.isPresent()) {
                System.out.println("Encontrado diretório de controllers: " + controllerDir.get());
                return controllerDir.get().toString();
            }
        } catch (IOException e) {
            System.err.println("Erro ao procurar diretório de controllers: " + e.getMessage());
        }

        // Se não encontrou nenhum diretório controller, usa o diretório raiz
        System.out.println("Nenhum diretório de controllers encontrado, usando diretório raiz: " + extractDir);
        return extractDir;
    }
}
