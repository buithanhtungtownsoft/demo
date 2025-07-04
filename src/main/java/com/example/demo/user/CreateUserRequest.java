package com.example.demo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private String companyCode;
    private String id;
    private String userName;
    private String password;
    private String email;
    private String authority;
    private String positionLevel;
    private String jobTitle;
    private String createdUser;
}