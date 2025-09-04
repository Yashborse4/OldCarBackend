package com.carselling.oldcar.security.jwt;

import com.carselling.oldcar.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for request interception and token validation
 * This filter intercepts all requests and validates JWT tokens
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateAccessToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    
                    // Additional validation - ensure token is still valid for this user
                    if (userDetails != null && !isTokenBlacklisted(jwt)) {
                        UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, 
                                        null, 
                                        userDetails.getAuthorities()
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        
                        log.debug("Successfully authenticated user: {}", username);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
            // Clear the security context on authentication failure
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from request header
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Check if token is blacklisted (for logout functionality)
     * This can be extended to check against a blacklist cache/database
     */
    private boolean isTokenBlacklisted(String token) {
        // TODO: Implement token blacklisting for logout functionality
        // For now, return false (no blacklisting)
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip JWT filter for public endpoints
        return path.startsWith("/api/auth/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/h2-console/") ||
               path.startsWith("/swagger-") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/ws") ||
               path.equals("/") ||
               path.equals("/favicon.ico") ||
               path.startsWith("/static/") ||
               path.startsWith("/public/");
    }
}
