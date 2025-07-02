package com.example.demo.auth;

import com.example.demo.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class AuthWebFilter implements WebFilter {

    @Autowired
    private JwtUtil JwtUtil;

    @Autowired
    private UserRepository userRepository;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPermitAllPath(path) || exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.extractClaims(token);

            String username = claims.getSubject();
            String companyCode = claims.get("companyCode", String.class);

            return userRepository.findByIdAndCompanyCode(username, companyCode)
                    .flatMap(user -> {
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                user, null, List.of()
                        );
                        SecurityContext context = new SecurityContextImpl(auth);
                        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        if (!exchange.getResponse().isCommitted()) {
                            log.warn(String.format("switchIfEmpty: %s", username));
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        return Mono.empty();
                    }))
                    .onErrorResume(e -> {
                        if (!exchange.getResponse().isCommitted()) {
                            log.warn(String.format("onErrorResumet: %s", username));
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        return Mono.empty();
                    });
        } catch (JwtException e) {
            e.printStackTrace();
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

    }

    private boolean isPermitAllPath(String path) {
        return AuthConstant.PERMIT_ALL_URLS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}