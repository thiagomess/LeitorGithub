package com.example.demo.controller;

import com.example.demo.dto.ApiRequest;
import com.example.demo.dto.ApiResponse;
import com.example.demo.service.GithubAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class GithubAnalyzerController {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalyzerController.class);
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("scope:\\s*([^,]+),\\s*url:\\s*(.+)");

    private final GithubAnalysisService analysisService;

    public GithubAnalyzerController(GithubAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public Mono<ResponseEntity<ApiResponse>> analyze(@RequestBody ApiRequest request) {
        log.info("Recebida requisição de análise: {}", request.message());

        String msg = request.message();
        if (msg != null) {
            Matcher matcher = MESSAGE_PATTERN.matcher(msg);
            if (matcher.matches()) {
                String scope = matcher.group(1).trim();
                String path = matcher.group(2).trim();

                log.info("Analisando - Scope: '{}', Path: '{}'", scope, path);
                return analysisService.analyzeRepository(scope, path);
            }
        }

        log.warn("Formato de mensagem inválido: {}", msg);
        String example = "{\"message\": \"scope: ping, url: ping/scope\"}";
        return Mono.just(ResponseEntity.badRequest()
                .body(new ApiResponse("O payload deve ser enviado como: " + example)));
    }
}
