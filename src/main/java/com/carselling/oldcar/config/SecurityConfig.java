package com.carselling.oldcar.config;

import com.carselling.oldcar.security.JwtAuthenticationEntryPoint;
import com.carselling.oldcar.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
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
 * Security Configuration with JWT authentication and role-based access control
 * Configures Spring Security, CORS, and authentication mechanisms
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Password encoder bean for secure password hashing
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Higher strength for better security
    }

    /**
     * Authentication provider configuration
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Authentication manager configuration
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS configuration for cross-origin requests
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow origins (configure based on your frontend URLs)
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",    // React development
                "http://localhost:4200",    // Angular development
                "http://localhost:8080",    // Vue development
                "https://yourdomain.com",   // Production frontend
                "https://*.yourdomain.com"  // Subdomains
        ));
        
        // Allow methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        ));
        
        // Allow headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", 
                "Origin", "Cache-Control", "X-File-Name", "X-File-Size"
        ));
        
        // Expose headers
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Content-Disposition"
        ));
        
        // Allow credentials
        configuration.setAllowCredentials(true);
        
        // Max age for preflight requests
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * Main security filter chain configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless JWT authentication
            .csrf(AbstractHttpConfigurer::disable)
            
            // Enable CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Configure session management to be stateless
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure exception handling
            .exceptionHandling(exceptions -> 
                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/cars/public/**").permitAll()
                .requestMatchers("/api/cars/search/**").permitAll()
                .requestMatchers("/api/cars/featured").permitAll()
                .requestMatchers("/api/cars/recent").permitAll()
                .requestMatchers("/api/cars/most-viewed").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cars").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cars/{id}").permitAll()
                
                // WebSocket endpoint
                .requestMatchers("/ws/**").permitAll()
                
                // Actuator endpoints (configure as needed)
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // Swagger/OpenAPI documentation (if enabled)
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                
                // Static resources
                .requestMatchers("/static/**", "/images/**", "/uploads/**").permitAll()
                
                // Error pages
                .requestMatchers("/error").permitAll()
                
                // Admin endpoints - ADMIN role required
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // Car management - SELLER, DEALER, or ADMIN roles
                .requestMatchers(HttpMethod.POST, "/api/cars").hasAnyRole("SELLER", "DEALER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/cars/**").hasAnyRole("SELLER", "DEALER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/cars/**").hasAnyRole("SELLER", "DEALER", "ADMIN")
                
                // User profile management - authenticated users
                .requestMatchers("/api/users/profile/**").hasRole("VIEWER")
                .requestMatchers("/api/users/me").hasRole("VIEWER")
                .requestMatchers(HttpMethod.PUT, "/api/users/profile").hasRole("VIEWER")
                .requestMatchers(HttpMethod.DELETE, "/api/users/profile").hasRole("VIEWER")
                
                // User management (admin operations)
                .requestMatchers("/api/users/all").hasRole("ADMIN")
                .requestMatchers("/api/users/search").hasRole("ADMIN")
                .requestMatchers("/api/users/statistics").hasRole("ADMIN")
                .requestMatchers("/api/users/role/**").hasRole("ADMIN")
                
                // Chat functionality - authenticated users
                .requestMatchers("/api/chat/**").hasRole("VIEWER")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // Add JWT authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Configure authentication provider
            .authenticationProvider(authenticationProvider());

        return http.build();
    }
}
