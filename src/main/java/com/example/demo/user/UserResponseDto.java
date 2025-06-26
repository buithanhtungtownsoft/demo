package com.example.demo.user;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserResponseDto {
    private UserModel currentUser;
    private List<UserModel> allUsers;
}