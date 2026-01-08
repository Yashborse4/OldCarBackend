package com.carselling.oldcar.security;

import com.carselling.oldcar.security.jwt.JwtAuthenticationEntryPoint;
import com.carselling.oldcar.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprehensive Spring Security Configuration
 * Configures authentication, authorization, CORS, and JWT security
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String corsAllowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String corsAllowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String corsAllowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean corsAllowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long corsMaxAge;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for REST API
                .csrf(AbstractHttpConfigurer::disable)

                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configure frame options for development (remove in production if not needed)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(contentTypeOptions -> {
                        })
                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true)))

                // Configure session management (stateless for JWT)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Configure authentication entry point
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))

                // Configure authorization rules
                .authorizeHttpRequests(authz -> authz
                        // =====================================================
                        // PUBLIC ENDPOINTS
                        // =====================================================

                        // Core Public Routes
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/favicon.ico", "/error").permitAll()
                        .requestMatchers("/static/**", "/public/**").permitAll()

                        // Authentication (Login, Register, OTP)
                        .requestMatchers("/api/auth/**").permitAll()

                        // WebSocket & System
                        .requestMatchers("/ws/**", "/ws-native/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()

                        // Search API is public
                        .requestMatchers("/api/search/**").permitAll()
                        .requestMatchers("/graphql").permitAll()

                        // =====================================================
                        // PROTECTED ENDPOINTS (Require Auth)
                        // Note: Order matters! More specific rules must come BEFORE general rules
                        // =====================================================

                        // User Profile
                        .requestMatchers("/api/user/**").authenticated()

                        // Dealer Specific Actions - MUST BE BEFORE /api/cars/** permitAll
                        .requestMatchers("/api/cars/dealer/**").hasAnyRole("USER", "DEALER", "ADMIN")
                        .requestMatchers("/api/cars/*/feature").hasAnyRole("DEALER", "ADMIN")
                        .requestMatchers("/api/cars/mycars").hasAnyRole("USER", "DEALER", "ADMIN")

                        // Car Management (Create/Update/Delete) - Requires Role
                        .requestMatchers(HttpMethod.POST, "/api/cars")
                        .hasAnyRole("USER", "DEALER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/cars/*")
                        .hasAnyRole("USER", "DEALER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/cars/*")
                        .hasAnyRole("USER", "DEALER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/cars/*")
                        .hasAnyRole("USER", "DEALER", "ADMIN")

                        // Public Car Viewing & Search - AFTER protected endpoints
                        .requestMatchers(HttpMethod.GET, "/api/cars", "/api/cars/*", "/api/cars/**")
                        .permitAll()

                        // Admin Only
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Communication
                        .requestMatchers("/api/chat/**").authenticated()

                        // Fallback: Secure everything else
                        .anyRequest().authenticated());

        // Add JWT authentication filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @SuppressWarnings("deprecation")
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        authProvider.setHideUserNotFoundExceptions(false); // For better error messages
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength 12 for better security
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            configuration.setAllowedOriginPatterns(Arrays.stream(corsAllowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .collect(Collectors.toList()));
        }

        if (corsAllowedMethods != null && !corsAllowedMethods.isBlank()) {
            configuration.setAllowedMethods(Arrays.stream(corsAllowedMethods.split(","))
                    .map(String::trim)
                    .filter(method -> !method.isEmpty())
                    .collect(Collectors.toList()));
        }

        if (corsAllowedHeaders != null && !corsAllowedHeaders.isBlank()) {
            if ("*".equals(corsAllowedHeaders.trim())) {
                configuration.setAllowedHeaders(List.of("*"));
            } else {
                configuration.setAllowedHeaders(Arrays.stream(corsAllowedHeaders.split(","))
                        .map(String::trim)
                        .filter(header -> !header.isEmpty())
                        .collect(Collectors.toList()));
            }
        }

        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"));

        configuration.setAllowCredentials(corsAllowCredentials);
        configuration.setMaxAge(corsMaxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
