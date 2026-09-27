package com.taskflow.config;

import com.taskflow.auth.JwtAuthFilter;
import com.taskflow.common.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final CorrelationIdFilter correlationIdFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, CorrelationIdFilter correlationIdFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.correlationIdFilter = correlationIdFilter;
    }

    @Bean
    org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var c = new org.springframework.web.cors.CorsConfiguration();
        c.setAllowedOrigins(java.util.List.of("http://localhost:5173"));
        c.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(java.util.List.of("*"));
        c.setAllowCredentials(true);
        var s = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/actuator/health")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, CorrelationIdFilter.class);
        return http.build();
    }
}
