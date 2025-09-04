package com.carselling.oldcar.util;

import com.carselling.oldcar.model.Role;
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
     * @return User ID or null if not authenticated
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        try {
            // If principal is a UserDetails object
            if (authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                return Long.parseLong(userDetails.getUsername());
            }
            
            // If principal is directly the user ID (as string)
            if (authentication.getPrincipal() instanceof String) {
                return Long.parseLong((String) authentication.getPrincipal());
            }
            
            // If principal is already a Long
            if (authentication.getPrincipal() instanceof Long) {
                return (Long) authentication.getPrincipal();
            }
            
        } catch (NumberFormatException e) {
            log.error("Failed to parse user ID from authentication: {}", authentication.getPrincipal());
        }
        
        return null;
    }

    /**
     * Get current authenticated user's username
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
     * @param role Role to check
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(Role role) {
        return hasRole("ROLE_" + role.name());
    }

    /**
     * Check if current user has a specific authority
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
        if (authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_SELLER"))) {
            return Optional.of(Role.SELLER);
        }
        if (authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_VIEWER"))) {
            return Optional.of(Role.VIEWER);
        }
        
        return Optional.empty();
    }

    /**
     * Check if the current user is the same as the specified user ID
     * @param userId User ID to check against
     * @return true if current user matches the specified ID
     */
    public static boolean isCurrentUser(Long userId) {
        Long currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(userId);
    }

    /**
     * Check if current user is authenticated
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
     * @return true if user is admin, false otherwise
     */
    public static boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /**
     * Check if current user can manage the specified user
     * (Admin can manage everyone, users can manage themselves)
     * @param userId User ID to check
     * @return true if current user can manage the specified user
     */
    public static boolean canManageUser(Long userId) {
        return isAdmin() || isCurrentUser(userId);
    }

    /**
     * Check if current user can view the specified user's information
     * @param userId User ID to check
     * @return true if current user can view the user's information
     */
    public static boolean canViewUser(Long userId) {
        // Admin can view all users, users can view themselves
        return isAdmin() || isCurrentUser(userId);
    }

    /**
     * Get current authentication object
     * @return Authentication object or null if not authenticated
     */
    public static Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
