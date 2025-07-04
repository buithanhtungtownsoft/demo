package com.example.demo.user;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<UserModel, String> {

    Mono<UserModel> findByIdAndCompanyCode(String id, String companyCode);

    @Query("INSERT INTO tb_user (company_code, id, user_name, password, email, authority, position_level, job_title, use_yn, " +
            "created_user, updated_user) VALUES (:companyCode, :id, :userName, :password, :email, :authority, :positionLevel, " +
            ":jobTitle, 'Y', :createdUser, :createdUser)")
    Mono<Void> insertUser(
            @Param("companyCode") String companyCode,
            @Param("id") String id,
            @Param("userName") String userName,
            @Param("password") String password,
            @Param("email") String email,
            @Param("authority") String authority,
            @Param("positionLevel") String positionLevel,
            @Param("jobTitle") String jobTitle,
            @Param("createdUser") String createdUser
    );

}
