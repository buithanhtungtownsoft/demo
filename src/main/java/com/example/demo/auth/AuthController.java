package com.example.demo.auth;

import com.example.demo.user.UserModel;
import com.example.demo.user.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@Log4j2
@Tag(name = "100.Auth API", description = "Auth API.")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(@RequestBody AuthRequest request) {
        return userRepository.findByIdAndCompanyCode(request.getId(), request.getCompanyCode())
            .flatMap(user -> {
                if (user == null) {
                    log.error("User not found: {}", request.getId());
                    return Mono.just(ResponseEntity.status(401).body(new AuthResponse("User not found")));
                }
                if (passwordEncoder.matches(request.getPw(), user.getPassword())) {
                    String token = jwtUtil.generateToken(user.getId(), user.getCompanyCode());
                    log.info("Login successful for user: {}", user.getId());
                    return Mono.just(ResponseEntity.ok(new AuthResponse(token)));
                } else {
                    log.warn("Invalid password for user: {}", user.getId());
                    return Mono.just(ResponseEntity.status(401).build());
                }
            })
            .switchIfEmpty(Mono.fromSupplier(() -> {
                log.warn("User not found for username: {}, companyCode: {}", request.getId(), request.getCompanyCode());
                return ResponseEntity.status(401).body(new AuthResponse("User not found"));
            }));
    }
}