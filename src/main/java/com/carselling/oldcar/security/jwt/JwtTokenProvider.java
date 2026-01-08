package com.carselling.oldcar.security.jwt;

import com.carselling.oldcar.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
                log.warn(
                        "JWT_SECRET not configured. Using fallback development secret. THIS IS NOT SECURE FOR PRODUCTION!");
                // Fallback 32-byte secret for development only
                jwtSecret = "jRdTUfn4xlZsyrpJvsn3ScbbCdh5ziaXReuetz9r0QF";
            }

            byte[] keyBytes;
            try {
                keyBytes = java.util.Base64.getDecoder().decode(jwtSecret);
                log.info("JWT secret decoded from base64");
            } catch (IllegalArgumentException e) {
                keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
                log.info(
                        "JWT secret is not base64 encoded. Using raw secret bytes; for best security, configure JWT_SECRET as a base64-encoded key.");
            }

            // Ensure key is at least 256 bits for HS256
            if (keyBytes.length < 32) {
                throw new IllegalArgumentException(
                        "JWT secret must be at least 256 bits (32 bytes) long. " +
                                "Current length: " + keyBytes.length + " bytes. " +
                                "Generate a secure key using: openssl rand -hex 32");
            }

            this.key = Keys.hmacShaKeyFor(keyBytes);
            log.info("JWT key initialized successfully with {}-bit key", keyBytes.length * 8);
        } catch (Exception e) {
            log.error("Failed to initialize JWT key", e);
            throw new RuntimeException("JWT key initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate comprehensive access token with user details
     */
    public String generateAccessToken(User user) {
        return generateToken(user, jwtExpirationMs);
    }

    /**
     * Generate minimal refresh token for security
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername()); // Add username to refresh token as well
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
    private String generateToken(User user, long expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername()); // Add username as a separate claim
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("location", user.getLocation());
        // Verification flags exposed for downstream services (and optionally frontend)
        claims.put("emailVerified", Boolean.TRUE.equals(user.getIsEmailVerified()));
        claims.put("verifiedDealer", Boolean.TRUE.equals(user.isDealerVerified()));
        claims.put("roles", getUserPermissions(user));
        claims.put("tokenType", "access");
        claims.put("createdAt",
                user.getCreatedAt() != null
                        ? user.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : 0L);

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
            case USER -> List.of("ROLE_USER", "car:read", "car:create", "car:update:own", "car:delete:own");
        };
    }

    /**
     * Extract username from token
     */
    public String getUsernameFromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("Attempted to extract username from null or empty token");
            return null;
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            // First try to get from explicit username claim, then fall back to subject
            String username = (String) claims.get("username");
            if (username != null) {
                return username;
            }
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
        if (token == null || token.trim().isEmpty()) {
            log.warn("Attempted to extract user ID from null or empty token");
            return null;
        }

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

    public void blacklistToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }

        long ttlSeconds = getTimeToExpiration(token);
        long expiresAtMillis = System.currentTimeMillis() + Math.max(ttlSeconds, 0) * 1000L;
        blacklistedTokens.put(token, expiresAtMillis);
    }

    public boolean isTokenBlacklisted(String token) {
        if (token == null) {
            return false;
        }

        Long expiresAtMillis = blacklistedTokens.get(token);
        if (expiresAtMillis == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (expiresAtMillis <= now) {
            blacklistedTokens.remove(token);
            return false;
        }

        return true;
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
     * Validate token
     */
    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("Token validation failed: token is null or empty");
            return false;
        }

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
     * Validate access token specifically
     */
    public boolean validateAccessToken(String token) {
        return validateToken(token) && isAccessToken(token);
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

    @SuppressWarnings("unchecked")
    public List<String> getAuthoritiesFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Object roles = claims.get("roles");
            if (roles instanceof List) {
                return (List<String>) roles;
            }

            // Fallback to role-based authorities
            String role = (String) claims.get("role");
            if (role != null) {
                return List.of("ROLE_" + role);
            }

            return List.of("ROLE_USER"); // Default role
        } catch (Exception e) {
            log.error("Failed to extract authorities from token", e);
            return List.of("ROLE_USER"); // Default role on error
        }
    }
}
