package com.example.demo.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@Tag(name = "500.User API", description = "User API.")
public class UserController {

    @Autowired
    UserService UserService;

    @GetMapping("/user")
    public Flux<UserModel> getUser() {
        return UserService.getAllUsers();
    }

    @GetMapping("/me")
    public Mono<UserModel> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(UserModel.class);
    }

    @GetMapping("/user-all")
    public Mono<UserResponseDto> getAllUsersAndCurrent() {
        Mono<UserModel> currentUserMono = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(UserModel.class);

        Flux<UserModel> allUsersFlux = UserService.getAllUsers();

        return currentUserMono.zipWith(allUsersFlux.collectList())
                .map(tuple -> new UserResponseDto(tuple.getT1(), tuple.getT2()));
    }

    @PostMapping("/user")
    public Mono<ResponseEntity<Object>> createUser(@RequestBody CreateUserRequest req) {
        return UserService.createUser(req)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(user))
                .onErrorResume(e -> {
                    e.printStackTrace();
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage()));
                });
    }

}
