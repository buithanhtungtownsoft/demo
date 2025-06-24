package com.example.demo.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class UserService {

    @Autowired
    private UserRepository UserRepository;

    public Flux<UserModel> getAllUsers() {
        return UserRepository.findAll();
    }

}
