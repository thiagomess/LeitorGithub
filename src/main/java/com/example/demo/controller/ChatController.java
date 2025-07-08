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
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("scope:\\s*([^,]+),\\s*url:\\s*(.+)");

    private final GithubAnalysisService analysisService;

    public ChatController(GithubAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/chat")
    public Mono<ResponseEntity<ApiResponse>> message(@RequestBody ApiRequest request) {
        log.info("Recebida requisição de análise: {}", request.message());

        String msg = request.message();
        if (msg != null && !msg.trim().isEmpty()) {
            Matcher matcher = MESSAGE_PATTERN.matcher(msg);
            if (matcher.matches()) {
                String scope = matcher.group(1).trim();
                String path = matcher.group(2).trim();

                log.info("Analisando com padrão scope/url - Scope: '{}', Path: '{}'", scope, path);
                return analysisService.analyzeRepository(scope, path);
            } else {
                log.info("Mensagem não segue o padrão scope/url, enviando diretamente para processamento: {}", msg);
                return analysisService.processDirectMessage(msg);
            }
        }

        log.warn("Mensagem vazia ou inválida");
        String example = "{\"message\": \"scope: ping, url: ping/scope\"}";
        return Mono.just(ResponseEntity.badRequest()
                .body(new ApiResponse(
                        "A mensagem não pode ser vazia. Para análise de repositório, use o formato: " + example)));
    }
}
