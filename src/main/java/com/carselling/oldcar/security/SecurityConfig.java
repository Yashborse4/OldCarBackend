package com.carselling.oldcar.security;

import com.carselling.oldcar.security.jwt.JwtAuthenticationEntryPoint;
import com.carselling.oldcar.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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
                    .contentTypeOptions(contentTypeOptions -> {})
                    .httpStrictTransportSecurity(hstsConfig -> 
                        hstsConfig
                            .maxAgeInSeconds(31536000)
                            .includeSubDomains(true)))
            
            // Configure session management (stateless for JWT)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure authentication entry point
            .exceptionHandling(exception -> 
                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/error").permitAll()
                
                // Authentication endpoints
                .requestMatchers("/api/auth/**").permitAll()
                
                // Public car viewing endpoints
                .requestMatchers(HttpMethod.GET, "/api/cars", "/api/cars/*").permitAll()
                
                // GraphQL endpoint
                .requestMatchers("/graphql").permitAll()
                
                // WebSocket endpoints
                .requestMatchers("/ws/**", "/ws-native/**").permitAll()
                
                // Actuator endpoints
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // Swagger/OpenAPI documentation (development)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                
                // Static resources
                .requestMatchers("/static/**", "/public/**").permitAll()
                
                // User profile endpoints
                .requestMatchers("/api/user/**").authenticated()
                
                // Car management endpoints
                .requestMatchers(HttpMethod.POST, "/api/cars").hasAnyRole("SELLER", "DEALER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/cars/*").hasAnyRole("SELLER", "DEALER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/cars/*").hasAnyRole("SELLER", "DEALER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/cars/*").hasAnyRole("SELLER", "DEALER", "ADMIN")
                .requestMatchers("/api/cars/mycars").hasAnyRole("SELLER", "DEALER", "ADMIN")
                .requestMatchers("/api/cars/*/feature").hasAnyRole("DEALER", "ADMIN")
                
                // Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // Chat endpoints
                .requestMatchers("/api/chat/**").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            );

        // Add JWT authentication filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
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
        
        // Allow specific origins (configure based on your frontend)
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",    // React development
            "http://localhost:8080",    // Alternative frontend port
            "http://localhost:19006",   // React Native Expo
            "https://*.yourdomain.com"  // Production domain (replace with actual)
        ));
        
        // Allow specific methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        ));
        
        // Allow specific headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With", 
            "Accept", 
            "Origin", 
            "Access-Control-Request-Method", 
            "Access-Control-Request-Headers"
        ));
        
        // Expose specific headers
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin", 
            "Access-Control-Allow-Credentials"
        ));
        
        // Allow credentials
        configuration.setAllowCredentials(true);
        
        // Cache preflight responses for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
