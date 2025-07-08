package com.example.demo.service;

import com.example.demo.dto.ControllerMatch;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class JavaAnalysisService {

    private final JavaParser javaParser;

    public JavaAnalysisService() {
        this.javaParser = new JavaParser();
    }

    public List<ControllerMatch> findControllersWithScopeAndPath(String directory, String scope, String path) {
        List<ControllerMatch> result = new ArrayList<>();

        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            System.err.println("Diretório não existe: " + directory);
            return result;
        }

        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(this::isJavaFile)
                    .forEach(javaPath -> {
                        processJavaFile(javaPath, scope, path, result);
                    });
        } catch (IOException e) {
            System.err.println("Erro ao percorrer diretório: " + directory + " - " + e.getMessage());
        }

        System.out.println("Encontrados " + result.size() + " matches no diretório: " + directory);
        return result;
    }

    private boolean isJavaFile(Path path) {
        return path.toString().endsWith(".java");
    }

    private void processJavaFile(Path javaPath, String scope, String path, List<ControllerMatch> result) {
        boolean scopeFound = false;
        boolean urlFound = false;
        String className = javaPath.getFileName().toString();

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(javaPath);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                    if (hasScope(method, scope)) {
                        scopeFound = true;
                    }
                    if (hasPath(method, path)) {
                        urlFound = true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao analisar arquivo: " + javaPath + " - " + e.getMessage());
        }

        if (scopeFound || urlFound) {
            result.add(new ControllerMatch(javaPath, className, scopeFound, urlFound, scope));
        }
    }

    private boolean hasScope(MethodDeclaration method, String scope) {
        Optional<AnnotationExpr> preAuthorize = method.getAnnotationByName("PreAuthorize");
        return preAuthorize.isPresent() &&
                preAuthorize.get().toString().contains("#oauth2.hasScope('" + scope + "')");
    }

    private boolean hasPath(MethodDeclaration method, String path) {
        Optional<AnnotationExpr> mapping = method.getAnnotationByName("GetMapping")
                .or(() -> method.getAnnotationByName("RequestMapping"));

        return mapping.isPresent() &&
                (mapping.get().toString().contains("path = \"" + path + "\"") ||
                        mapping.get().toString().contains("\"" + path + "\""));
    }
}
