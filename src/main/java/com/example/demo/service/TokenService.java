package com.example.demo.service;

import com.example.demo.client.OAuth2Client;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TokenService {

    private final OAuth2Client oauth2Client;

    public TokenService(OAuth2Client oauth2Client) {
        this.oauth2Client = oauth2Client;
    }

    public Mono<String> getCurrentToken() {
        return oauth2Client.getAccessToken();
    }

    public void invalidateToken() {
        oauth2Client.invalidateToken();
    }
}
