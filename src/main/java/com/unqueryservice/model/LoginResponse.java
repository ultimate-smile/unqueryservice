package com.unqueryservice.model;

import lombok.Builder;
import lombok.Data;

/**
 * Authentication response containing a signed JWT.
 */
@Data
@Builder
public class LoginResponse {

    private String token;
    private String tokenType;
    private long expiresIn;
    private String username;
}
