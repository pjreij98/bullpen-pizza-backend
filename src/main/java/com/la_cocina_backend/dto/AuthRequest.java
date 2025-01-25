package com.la_cocina_backend.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String email;
    private String password;

}
