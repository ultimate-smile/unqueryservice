package com.unqueryservice.controller;

import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.model.LoginRequest;
import com.unqueryservice.model.LoginResponse;
import com.unqueryservice.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Handles user authentication and JWT token issuance.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final QueryServiceProperties properties;

    /**
     * POST /api/auth/login
     *
     * <p>Authenticates the user and returns a signed JWT on success.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        String token = tokenProvider.generateToken(authentication);

        log.info("User '{}' authenticated successfully", request.getUsername());

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(properties.getJwt().getExpirationMs() / 1000)
                .username(authentication.getName())
                .build();

        return ResponseEntity.ok(response);
    }
}
