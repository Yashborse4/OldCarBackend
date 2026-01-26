package com.carselling.oldcar.util;

import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Optional;

/**
 * Utility class for security-related operations
 * Provides methods to get current user information from security context
 */
@Slf4j
public class SecurityUtils {

    private SecurityUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Get current authenticated user's ID
     * 
     * @return User ID or null if not authenticated
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getPrincipal() == null ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        try {
            Object principal = authentication.getPrincipal();

            // If principal is a UserPrincipal (our custom class), use getId() directly
            if (principal instanceof UserPrincipal) {
                UserPrincipal userPrincipal = (UserPrincipal) principal;
                return userPrincipal.getId();
            }

            // If principal is directly the user ID (as string)
            if (principal instanceof String) {
                String principalStr = (String) principal;
                if (principalStr == null || principalStr.trim().isEmpty()) {
                    return null;
                }
                return Long.parseLong(principalStr);
            }

            // If principal is already a Long
            if (principal instanceof Long) {
                return (Long) principal;
            }

            // Fallback: try to get username and parse as Long (for other UserDetails
            // implementations)
            if (principal instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) principal;
                String username = userDetails.getUsername();
                if (username == null || username.trim().isEmpty()) {
                    return null;
                }
                return Long.parseLong(username);
            }

        } catch (NumberFormatException e) {
            log.error("Failed to parse user ID from authentication: {}", authentication.getPrincipal());
        } catch (Exception e) {
            log.error("Unexpected error parsing user ID from authentication: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get current authenticated user's username
     * 
     * @return Username or null if not authenticated
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }

        return authentication.getName();
    }

    /**
     * Get current authenticated user's authorities/roles
     * 
     * @return Collection of authorities or empty collection if not authenticated
     */
    public static Collection<? extends GrantedAuthority> getCurrentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return java.util.Collections.emptyList();
        }

        return authentication.getAuthorities();
    }

    /**
     * Check if current user has a specific role
     * 
     * @param role Role to check
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(Role role) {
        return hasRole("ROLE_" + role.name());
    }

    /**
     * Check if current user has a specific authority
     * 
     * @param authority Authority to check (e.g., "ROLE_ADMIN")
     * @return true if user has the authority, false otherwise
     */
    public static boolean hasRole(String authority) {
        Collection<? extends GrantedAuthority> authorities = getCurrentUserAuthorities();

        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(authority));
    }

    /**
     * Check if current user has any of the specified roles
     * 
     * @param roles Roles to check
     * @return true if user has any of the roles, false otherwise
     */
    public static boolean hasAnyRole(Role... roles) {
        for (Role role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if current user has any of the specified authorities
     * 
     * @param authorities Authorities to check
     * @return true if user has any of the authorities, false otherwise
     */
    public static boolean hasAnyAuthority(String... authorities) {
        for (String authority : authorities) {
            if (hasRole(authority)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get current user's highest role
     * 
     * @return Highest role or null if not authenticated
     */
    public static Optional<Role> getCurrentUserHighestRole() {
        Collection<? extends GrantedAuthority> authorities = getCurrentUserAuthorities();

        // Check roles in order of hierarchy (highest to lowest)
        if (authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))) {
            return Optional.of(Role.ADMIN);
        }
        if (authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_DEALER"))) {
            return Optional.of(Role.DEALER);
        }
        if (authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_USER"))) {
            return Optional.of(Role.USER);
        }

        return Optional.empty();
    }

    /**
     * Check if the current user is the same as the specified user ID
     * 
     * @param userId User ID to check against
     * @return true if current user matches the specified ID
     */
    public static boolean isCurrentUser(Long userId) {
        Long currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(userId);
    }

    /**
     * Check if current user is authenticated
     * 
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication != null &&
                authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * Check if current user is an admin
     * 
     * @return true if user is admin, false otherwise
     */
    public static boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /**
     * Check if current user can manage the specified user
     * (Admin can manage everyone, users can manage themselves)
     * 
     * @param userId User ID to check
     * @return true if current user can manage the specified user
     */
    public static boolean canManageUser(Long userId) {
        return isAdmin() || isCurrentUser(userId);
    }

    /**
     * Check if current user can view the specified user's information
     * 
     * @param userId User ID to check
     * @return true if current user can view the user's information
     */
    public static boolean canViewUser(Long userId) {
        // Admin can view all users, users can view themselves
        return isAdmin() || isCurrentUser(userId);
    }

    /**
     * Get current authentication object
     * 
     * @return Authentication object or null if not authenticated
     */
    public static Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Extract username from email address (part before @ symbol)
     * Removes digits and extracts core username
     * Example: yashborse432005@gmail.com -> yash
     * 
     * @param email Email address to extract username from
     * @return Extracted username or null if email is invalid
     */
    public static String extractUsernameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }

        // Get the local part (before @)
        String localPart = email.substring(0, email.indexOf("@"));

        // Remove all digits to get the alphabetic portion
        String alphabeticPart = localPart.replaceAll("[0-9]", "");

        // If the alphabetic part is too long, take a reasonable portion (first 4-8
        // chars)
        // This handles cases like "yashborse" -> "yash"
        String username = alphabeticPart;
        if (alphabeticPart.length() > 8) {
            // Try to find a natural break point (uppercase letter, underscore, dot)
            int breakPoint = -1;
            for (int i = 1; i < Math.min(alphabeticPart.length(), 12); i++) {
                char c = alphabeticPart.charAt(i);
                if (Character.isUpperCase(c) || c == '_' || c == '.') {
                    breakPoint = i;
                    break;
                }
            }

            if (breakPoint > 3) {
                username = alphabeticPart.substring(0, breakPoint);
            } else {
                // No natural break, take first 8 characters
                username = alphabeticPart.substring(0, 8);
            }
        } else if (alphabeticPart.length() > 5) {
            // For medium length (6-8 chars), take first 4-5 characters
            username = alphabeticPart.substring(0, Math.min(5, alphabeticPart.length()));
        }

        // Ensure minimum length
        if (username.length() < 3) {
            // If too short after processing, use original local part
            username = localPart;
            if (username.length() < 3) {
                username = username + "_user";
            }
        }

        // Sanitize username to ensure it only contains valid characters
        username = username.replaceAll("[^a-zA-Z0-9_.-]", "_");

        return username;
    }
}
