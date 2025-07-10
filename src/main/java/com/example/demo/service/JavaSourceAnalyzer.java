package com.example.demo.service;

import com.example.demo.dto.ControllerMatch;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class JavaSourceAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(JavaSourceAnalyzer.class);
    private final JavaParser javaParser;

    public JavaSourceAnalyzer() {
        this.javaParser = new JavaParser();
    }

    public List<ControllerMatch> analyzeJavaFiles(String directory, String scope, String path) {
        List<ControllerMatch> result = new ArrayList<>();

        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            log.warn("Diretório não existe: {}", directory);
            return result;
        }

        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaPath -> analyzeJavaFile(javaPath, scope, path, result));
        } catch (IOException e) {
            log.error("Erro ao percorrer diretório {}: {}", directory, e.getMessage());
        }

        return result;
    }

    private void analyzeJavaFile(Path javaPath, String scope, String path, List<ControllerMatch> result) {
        boolean scopeFound = false;
        boolean pathFound = false;
        String className = javaPath.getFileName().toString();

        log.debug("Analisando arquivo Java: {}", javaPath);

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(javaPath);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                boolean isController = cu.findAll(AnnotationExpr.class).stream()
                        .anyMatch(a -> a.getNameAsString().equals("RestController") ||
                                a.getNameAsString().equals("Controller"));

                if (isController) {
                    log.debug("Arquivo {} é um controller, analisando métodos", className);
                    for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                        if (hasScope(method, scope)) {
                            scopeFound = true;
                        }
                        if (hasPath(method, path)) {
                            pathFound = true;
                            log.info("Encontrado método com path '{}' em {}: {}", path, className,
                                    method.getNameAsString());
                        }
                    }
                }
            } else {
                log.debug("Falha ao analisar arquivo: {}", javaPath);
            }
        } catch (Exception e) {
            log.warn("Erro ao analisar arquivo {}: {}", javaPath, e.getMessage(), e);
        }

        if (scopeFound || pathFound) {
            result.add(new ControllerMatch(javaPath, className, scopeFound, pathFound, scope));
            log.debug("Match encontrado em {}: scope={}, path={}", className, scopeFound, pathFound);
        }
    }

    private boolean hasScope(MethodDeclaration method, String scope) {
        Optional<AnnotationExpr> preAuthorize = method.getAnnotationByName("PreAuthorize");
        if (!preAuthorize.isPresent()) {
            return false;
        }

        String annotation = preAuthorize.get().toString();
        log.debug("Verificando anotação @PreAuthorize: {}", annotation);

        boolean hasHashScope = annotation.contains("#oauth2.hasScope('" + scope + "')");
        boolean hasNoHashScope = annotation.contains("oauth2.hasScope('" + scope + "')");

        if (hasHashScope || hasNoHashScope) {
            log.info("Encontrado método com scope '{}' em {}: {} (formato: {})",
                    scope,
                    method.getNameAsString(),
                    annotation,
                    hasHashScope ? "com #" : "sem #");
            return true;
        }

        return false;
    }

    private boolean hasPath(MethodDeclaration method, String path) {
        Optional<AnnotationExpr> mapping = method.getAnnotationByName("GetMapping")
                .or(() -> method.getAnnotationByName("PostMapping"))
                .or(() -> method.getAnnotationByName("RequestMapping"));

        if (!mapping.isPresent()) {
            return false;
        }

        String annotation = mapping.get().toString();
        log.debug("Verificando anotação de mapeamento: {}", annotation);

        boolean hasPathAttribute = annotation.contains("path = \"" + path + "\"");
        boolean hasValueAttribute = annotation.contains("value = \"" + path + "\"");
        boolean hasDirectPath = annotation.contains("\"" + path + "\"");

        if (hasPathAttribute || hasValueAttribute || hasDirectPath) {
            log.info("Encontrado método com path '{}' em {}: {}",
                    path,
                    method.getNameAsString(),
                    annotation);
            return true;
        }

        return false;
    }
}
