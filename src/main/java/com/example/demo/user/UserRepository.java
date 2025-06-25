package com.example.demo.user;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<UserModel, String> {

    Mono<UserModel> findByIdAndCompanyCode(String id, String companyCode);

}
