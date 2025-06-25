package com.example.demo.auth;

import com.example.demo.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AuthWebFilter implements WebFilter {

    private final UserRepository userRepository;

    @Autowired
    public AuthWebFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String username = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (username != null) {
            return userRepository.findById(username)
                    .flatMap(user -> chain.filter(exchange))
                    .switchIfEmpty(Mono.defer(() -> {
                        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }));
        }
        return chain.filter(exchange);
    }
}