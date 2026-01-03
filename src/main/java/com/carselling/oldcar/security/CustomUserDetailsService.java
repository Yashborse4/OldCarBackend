package com.carselling.oldcar.security;

import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom User Details Service for Spring Security integration
 * Loads user details from the database for authentication and authorization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        log.debug("Loading user details for: {}", usernameOrEmail);
        
        try {
            User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            String.format("User not found with username or email: %s", usernameOrEmail)));

            log.debug("Successfully loaded user: {} with role: {}", user.getUsername(), user.getRole());
            return UserPrincipal.from(user);
            
        } catch (Exception e) {
            log.error("Error loading user details for: {}", usernameOrEmail, e);
            throw new UsernameNotFoundException("Failed to load user details", e);
        }
    }

    /**
     * Load user details by user ID
     * Used for JWT token authentication
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        log.debug("Loading user details by ID: {}", userId);
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            String.format("User not found with id: %s", userId)));

            log.debug("Successfully loaded user by ID: {} - {}", userId, user.getUsername());
            return UserPrincipal.from(user);
            
        } catch (Exception e) {
            log.error("Error loading user details by ID: {}", userId, e);
            throw new UsernameNotFoundException("Failed to load user details by ID", e);
        }
    }

    /**
     * Check if user exists by username or email
     */
    @Transactional(readOnly = true)
    public boolean userExists(String usernameOrEmail) {
        try {
            return userRepository.existsByUsernameOrEmail(usernameOrEmail, usernameOrEmail);
        } catch (Exception e) {
            log.error("Error checking user existence for: {}", usernameOrEmail, e);
            return false;
        }
    }

    /**
     * Check if user is active and not locked
     */
    @Transactional(readOnly = true)
    public boolean isUserActiveAndUnlocked(String usernameOrEmail) {
        try {
            return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                    .map(user -> Boolean.TRUE.equals(user.getIsActive()) && user.isAccountNonLocked())
                    .orElse(false);
        } catch (Exception e) {
            log.error("Error checking user status for: {}", usernameOrEmail, e);
            return false;
        }
    }
}
