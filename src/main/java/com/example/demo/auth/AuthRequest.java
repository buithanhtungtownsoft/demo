package com.example.demo.auth;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthRequest {
    private String companyCode;
    private String id;
    private String pw;
}