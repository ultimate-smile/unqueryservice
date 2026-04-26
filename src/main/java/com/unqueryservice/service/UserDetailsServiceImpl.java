package com.unqueryservice.service;

import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Service;

/**
 * Default in-memory user store for demonstration purposes.
 *
 * <p>In production, replace this with a database-backed {@link UserDetailsService}
 * (e.g. via Spring Data JPA) to support dynamic user management.
 *
 * <p>Default users (passwords encoded with BCrypt):
 * <ul>
 *   <li>{@code admin} / {@code admin123} – role {@code ROLE_ADMIN}</li>
 *   <li>{@code analyst} / {@code analyst123} – role {@code ROLE_ANALYST}</li>
 *   <li>{@code viewer} / {@code viewer123} – role {@code ROLE_VIEWER}</li>
 * </ul>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final InMemoryUserDetailsManager delegate;

    public UserDetailsServiceImpl(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails analyst = User.builder()
                .username("analyst")
                .password(passwordEncoder.encode("analyst123"))
                .roles("ANALYST")
                .build();

        UserDetails viewer = User.builder()
                .username("viewer")
                .password(passwordEncoder.encode("viewer123"))
                .roles("VIEWER")
                .build();

        this.delegate = new InMemoryUserDetailsManager(admin, analyst, viewer);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return delegate.loadUserByUsername(username);
    }
}
