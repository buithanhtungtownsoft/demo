package com.example.demo.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class UserService {

    @Autowired
    private UserRepository UserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Flux<UserModel> getAllUsers() {
        return UserRepository.findAll();
    }

    public Mono<Object> createUser(CreateUserRequest req) {

        return UserRepository.findByIdAndCompanyCode(req.getId(), req.getCompanyCode())
                .flatMap(existing -> Mono.error(new IllegalStateException("User already exists")))
                .switchIfEmpty(Mono.defer(() -> {
                    UserModel user = new UserModel();
                    user.setId(req.getId());
                    user.setCompanyCode(req.getCompanyCode());
                    user.setUserName(req.getUserName());
                    user.setPassword(passwordEncoder.encode(req.getPassword())); // BCrypt
                    user.setEmail(req.getEmail());
                    user.setAuthority(req.getAuthority());
                    user.setPositionLevel(req.getPositionLevel());
                    user.setJobTitle(req.getJobTitle());
                    user.setUseYn("Y");
                    user.setCreatedUser(req.getCreatedUser());
                    user.setUpdatedUser(req.getCreatedUser());
                    user.setCreatedAt(LocalDateTime.now());
                    user.setUpdatedAt(LocalDateTime.now());

                    return UserRepository.insertUser(
                            user.getCompanyCode(),
                            user.getId(),
                            user.getUserName(),
                            user.getPassword(),
                            user.getEmail(),
                            user.getAuthority(),
                            user.getPositionLevel(),
                            user.getJobTitle(),
                            user.getCreatedUser()
                    );
                }));
    }

}
