package com.example.demo.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserId implements Serializable {
    private String companyCode;
    private String id;
}
