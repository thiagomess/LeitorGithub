package com.example.demo.client;

import reactor.core.publisher.Mono;

/**
 * Interface comum para clientes de API.
 * Define métodos básicos que todos os clientes devem implementar.
 */
public interface ApiClient {

    default Mono<String> authenticate() {
        return Mono.just("");
    }

}
