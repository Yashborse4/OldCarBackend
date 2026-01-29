package com.carselling.oldcar.security;

import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Utility class for extracting user information from Authentication context.
 * Centralizes the pattern of extracting User/UserId from Spring Security's
 * Authentication.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationHelper {

    private final UserRepository userRepository;

    /**
     * Get the current user ID from authentication.
     * 
     * @param authentication Spring Security Authentication object
     * @return User ID
     * @throws IllegalStateException if not authenticated
     */
    public Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getId();
        } else if (principal instanceof User) {
            return ((User) principal).getId();
        }

        throw new IllegalStateException("Unable to extract user ID from authentication");
    }

    /**
     * Get the full User entity from authentication.
     * 
     * @param authentication Spring Security Authentication object
     * @return User entity
     * @throws ResourceNotFoundException if user not found
     */
    public User getCurrentUser(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
    }

    /**
     * Check if the current user is authenticated.
     * 
     * @param authentication Spring Security Authentication object
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
