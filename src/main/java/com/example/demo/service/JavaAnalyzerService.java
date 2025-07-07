package com.example.demo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.demo.dto.ControllerMatch;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

@Service
public class JavaAnalyzerService {

    public List<ControllerMatch> findControllersWithScopeAndPath(String dir, String scope, String path) throws IOException {
        List<ControllerMatch> result = new ArrayList<>();
        JavaParser parser = new JavaParser();
        Files.walk(Paths.get(dir))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaPath -> {
                boolean scopeFound = false;
                boolean urlFound = false;
                String className = javaPath.getFileName().toString();
                try {
                    ParseResult<CompilationUnit> parseResult = parser.parse(javaPath);
                    if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                        CompilationUnit cu = parseResult.getResult().get();
                        for (var method : cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
                            Optional<com.github.javaparser.ast.expr.AnnotationExpr> preAuthorize =
                                    method.getAnnotationByName("PreAuthorize");
                            Optional<com.github.javaparser.ast.expr.AnnotationExpr> getMapping =
                                    method.getAnnotationByName("GetMapping")
                                    .or(() -> method.getAnnotationByName("RequestMapping"));

                            if (preAuthorize.isPresent() &&
                                preAuthorize.get().toString().contains("#oauth2.hasScope('" + scope + "')")) {
                                scopeFound = true;
                            }
                            if (getMapping.isPresent() && (
                                    getMapping.get().toString().contains("path = \"" + path + "\"") ||
                                    getMapping.get().toString().contains("\"" + path + "\""))) {
                                urlFound = true;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                if (scopeFound || urlFound) {
                    result.add(new ControllerMatch(javaPath, className, scopeFound, urlFound, scope));
                }
            });
        return result;
    }
}
