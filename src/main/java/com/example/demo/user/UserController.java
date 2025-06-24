package com.example.demo.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@Slf4j
public class UserController {

    @Autowired
    UserService UserService;

    @GetMapping("/user")
    public Flux<UserModel> getUser() {
        return UserService.getAllUsers();
    }

}
