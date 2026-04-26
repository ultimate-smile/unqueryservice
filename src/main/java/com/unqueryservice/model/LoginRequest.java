package com.unqueryservice.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Authentication request body.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "username must not be blank")
    private String username;

    @NotBlank(message = "password must not be blank")
    private String password;
}
