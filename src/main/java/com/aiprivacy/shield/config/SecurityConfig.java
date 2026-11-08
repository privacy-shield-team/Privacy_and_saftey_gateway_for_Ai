package com.aiprivacy.shield.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Spring Security configuration for ProtectoShield.
 *
 * Authentication strategy:
 * - Session-based (HttpSession with HttpOnly cookie)
 * - Our own /auth/* endpoints handle login/register/logout
 * - Spring Security protects route access; actual credential check is in AuthController
 * - API endpoints require an authenticated session (checked via SessionAuthFilter)
 * - Static files and auth endpoints are public
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Use /auth/login endpoint for authentication");
        };
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable Spring's built-in form login — we use our own login.html
            .formLogin(form -> form.disable())
            // Disable HTTP Basic — not needed
            .httpBasic(basic -> basic.disable())
            // Disable CSRF for JSON API endpoints (session cookie is HttpOnly + SameSite)
            .csrf(csrf -> csrf.disable())
            // Session management
            .sessionManagement(session -> session
                .maximumSessions(5)
                .expiredUrl("/login.html?expired=true")
            )
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public static assets and auth endpoints
                .requestMatchers(
                    "/login.html",
                    "/styles.css",
                    "/app.js",
                    "/favicon.ico",
                    "/auth/**",
                    "/api/health",
                    "/h2-console/**"
                ).permitAll()
                // All API endpoints and main app require session auth (checked in filter)
                .anyRequest().permitAll()  // Filter handles session check for /api/*
            )
            // Allow H2 console frames
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        // Add our custom session-checking filter before Spring's UsernamePasswordAuthenticationFilter
        http.addFilterBefore(new SessionAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
