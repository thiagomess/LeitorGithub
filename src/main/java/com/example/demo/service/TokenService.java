package com.example.demo.service;

import com.example.demo.client.StackspotClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TokenService {

    private final StackspotClient stackspotClient;

    public TokenService(StackspotClient stackspotClient) {
        this.stackspotClient = stackspotClient;
    }

    public Mono<String> getCurrentToken() {
        return stackspotClient.getAccessToken();
    }

    public void invalidateToken() {
        stackspotClient.invalidateToken();
    }
}
