package com.example.demo.auth;

import com.example.demo.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@AllArgsConstructor
public class AuthWebFilter implements WebFilter {

    private JwtUtil JwtUtil;

    private UserRepository userRepository;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        log.info("Processing filter for path={}, method={}, thread={}",
                path, exchange.getRequest().getMethod(), Thread.currentThread().getName());

        // Bỏ qua các path không cần xác thực
        if (isPermitAllPath(path) || exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Kiểm tra nếu filter đã được gọi
        if (Boolean.TRUE.equals(exchange.getAttribute("FILTER_ALREADY_CALLED"))) {
            log.warn("Filter re-invoked! path={}", path);
            Object user = exchange.getAttribute("AUTH_USER");
            if (user != null) {
                Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
                SecurityContext context = new SecurityContextImpl(auth);
                return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
            }
            log.warn("No AUTH_USER found, skipping re-invoked filter");
            return chain.filter(exchange);
        }
        exchange.getAttributes().put("FILTER_ALREADY_CALLED", true);

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path={}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.extractClaims(token);
            String username = claims.getSubject();
            String companyCode = claims.get("companyCode", String.class);

            return userRepository.findByIdAndCompanyCode(username, companyCode)
                    .switchIfEmpty(Mono.error(new RuntimeException("User not found or company code mismatch")))
                    .flatMap(user -> {
                        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
                        SecurityContext context = new SecurityContextImpl(auth);
                        exchange.getAttributes().put("AUTH_USER", user);
                        log.info("User authenticated: username={}, companyCode={}", username, companyCode);
                        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                    })
                    .onErrorResume(e -> {
                        log.error("Authentication error for path={}: {}", path, e.getMessage());
                        if (!exchange.getResponse().isCommitted()) {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        return Mono.empty();
                    });
        } catch (JwtException e) {
            log.error("Invalid JWT token for path={}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPermitAllPath(String path) {
        return AuthConstant.PERMIT_ALL_URLS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}