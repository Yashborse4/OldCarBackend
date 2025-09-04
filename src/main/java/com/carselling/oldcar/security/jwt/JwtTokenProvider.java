package com.carselling.oldcar.security.jwt;

import com.carselling.oldcar.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT Token Provider with comprehensive token management
 * Supports both access and refresh tokens with enhanced user details
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private Key key;

    @PostConstruct
    public void init() {
        try {
            // Decode base64 secret if it's encoded, otherwise use as-is
            byte[] keyBytes;
            try {
                keyBytes = java.util.Base64.getDecoder().decode(jwtSecret);
            } catch (IllegalArgumentException e) {
                // If decoding fails, use the secret as plain bytes
                keyBytes = jwtSecret.getBytes();
            }
            
            // Ensure key is at least 256 bits for HS256
            if (keyBytes.length < 32) {
                throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 bytes) long");
            }
            
            this.key = Keys.hmacShaKeyFor(keyBytes);
            log.info("JWT key initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize JWT key", e);
            throw new RuntimeException("JWT key initialization failed", e);
        }
    }

    /**
     * Generate comprehensive access token with user details
     */
    public String generateAccessToken(User user) {
        return generateToken(user, jwtExpirationMs, "access");
    }

    /**
     * Generate minimal refresh token for security
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("tokenType", "refresh");
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate token with comprehensive user details
     */
    private String generateToken(User user, long expirationMs, String tokenType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("location", user.getLocation());
        claims.put("roles", getUserPermissions(user));
        claims.put("tokenType", tokenType);
        claims.put("createdAt", user.getCreatedAt() != null ? 
                user.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : null);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Get user permissions based on role
     */
    private List<String> getUserPermissions(User user) {
        return switch (user.getRole()) {
            case ADMIN -> List.of("ROLE_ADMIN", "car:read", "car:create", "car:update:any", 
                                 "car:delete:any", "car:feature", "user:manage", "analytics:view");
            case DEALER -> List.of("ROLE_DEALER", "car:read", "car:create", "car:update:own", 
                                  "car:delete:own", "car:feature", "analytics:view");
            case SELLER -> List.of("ROLE_SELLER", "car:read", "car:create", "car:update:own", 
                                  "car:delete:own");
            case VIEWER -> List.of("ROLE_VIEWER", "car:read");
        };
    }

    /**
     * Extract username from token
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Failed to extract username from token", e);
            return null;
        }
    }

    /**
     * Extract user ID from token
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            Object userId = claims.get("userId");
            if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            } else if (userId instanceof Long) {
                return (Long) userId;
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to extract user ID from token", e);
            return null;
        }
    }

    /**
     * Extract email from token
     */
    public String getEmailFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return (String) claims.get("email");
        } catch (Exception e) {
            log.error("Failed to extract email from token", e);
            return null;
        }
    }

    /**
     * Extract role from token
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return (String) claims.get("role");
        } catch (Exception e) {
            log.error("Failed to extract role from token", e);
            return null;
        }
    }

    /**
     * Extract location from token
     */
    public String getLocationFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return (String) claims.get("location");
        } catch (Exception e) {
            log.error("Failed to extract location from token", e);
            return null;
        }
    }

    /**
     * Extract token type
     */
    public String getTokenType(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return (String) claims.get("tokenType");
        } catch (Exception e) {
            log.error("Failed to extract token type from token", e);
            return null;
        }
    }

    /**
     * Check if token is access token
     */
    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    /**
     * Check if token is refresh token
     */
    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    /**
     * Get token expiration date
     */
    public Date getExpirationDateFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("Failed to extract expiration date from token", e);
            return null;
        }
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration != null && expiration.before(new Date());
    }

    /**
     * Get remaining time until token expires (in seconds)
     */
    public long getTimeToExpiration(String token) {
        Date expiration = getExpirationDateFromToken(token);
        if (expiration == null) {
            return 0;
        }
        long timeToExpire = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(0, timeToExpire);
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token format", e);
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token", e);
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token", e);
        } catch (IllegalArgumentException e) {
            log.error("JWT token compact of handler are invalid", e);
        } catch (Exception e) {
            log.error("JWT token validation failed", e);
        }
        return false;
    }

    /**
     * Validate refresh token specifically
     */
    public boolean validateRefreshToken(String token) {
        return validateToken(token) && isRefreshToken(token);
    }

    /**
     * Validate access token specifically
     */
    public boolean validateAccessToken(String token) {
        return validateToken(token) && isAccessToken(token);
    }

    /**
     * Get comprehensive user details from token
     */
    public Map<String, Object> getUserDetailsFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Map<String, Object> userDetails = new HashMap<>();
            userDetails.put("username", claims.getSubject());
            userDetails.put("userId", claims.get("userId"));
            userDetails.put("email", claims.get("email"));
            userDetails.put("role", claims.get("role"));
            userDetails.put("location", claims.get("location"));
            userDetails.put("roles", claims.get("roles"));
            userDetails.put("tokenType", claims.get("tokenType"));
            userDetails.put("issuedAt", claims.getIssuedAt());
            userDetails.put("expiresAt", claims.getExpiration());
            userDetails.put("createdAt", claims.get("createdAt"));

            return userDetails;
        } catch (Exception e) {
            log.error("Failed to extract user details from token", e);
            return new HashMap<>();
        }
    }

    /**
     * Get access token expiration in milliseconds
     */
    public long getAccessTokenExpiration() {
        return jwtExpirationMs;
    }

    /**
     * Get refresh token expiration in milliseconds
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpirationMs;
    }
}
