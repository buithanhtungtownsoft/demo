package com.example.demo.auth;

import java.util.List;

public class AuthConstant {

    public static final List<String> PERMIT_ALL_URLS = List.of(
            "/auth/login",

            "/swagger-ui/**",
            "/swagger-ui/swagger-ui-bundle.js",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/webjars/swagger-ui/**",
            "/webjars/**",
            "/favicon.ico"
    );

}
