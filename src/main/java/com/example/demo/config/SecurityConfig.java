package com.example.demo.config;

import com.example.demo.auth.AuthWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfig {

    public static final List<String> PERMIT_ALL_URLS = List.of(
            "/auth/login",
            "/auth/token/refresh",
            "/swagger-ui/**",
            "/swagger-ui/swagger-ui-bundle.js",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "webjars/swagger-ui/**",
            "webjars/**",
            "favicon.ico"
    );

    @Bean
    public SecurityWebFilterChain securitygWebFilterChain(ServerHttpSecurity http, AuthWebFilter authWebFilter) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(ex -> ex
                        .pathMatchers(PERMIT_ALL_URLS.toArray(new String[0])).permitAll()
                        .anyExchange().authenticated())
                .addFilterBefore(authWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

