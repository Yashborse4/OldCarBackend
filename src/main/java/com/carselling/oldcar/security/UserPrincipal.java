package com.carselling.oldcar.security;

import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean active;
    private final boolean emailVerified;
    private final boolean verifiedDealer;
    private final LocalDateTime lockedUntil;

    private UserPrincipal(
            Long id,
            String email,
            String password,
            Role role,
            boolean active,
            boolean emailVerified,
            boolean verifiedDealer,
            LocalDateTime lockedUntil) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
        this.active = active;
        this.emailVerified = emailVerified;
        this.verifiedDealer = verifiedDealer;
        this.lockedUntil = lockedUntil;
    }

    /**
     * Factory method to convert User entity → UserPrincipal
     */
    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole(),
                user.isActive(),
                Boolean.TRUE.equals(user.getIsEmailVerified()),
                Boolean.TRUE.equals(user.isDealerVerified()),
                user.getLockedUntil());
    }

    /**
     * Authorities used by Spring Security
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Username for authentication (email-based login)
     */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Account state checks
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Account is locked if lockedUntil is set and is in the future
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // User is enabled only if active AND email is verified
        return active && emailVerified;
    }

    /**
     * Custom getters (useful in controllers & security checks)
     */
    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public boolean isVerifiedDealer() {
        return verifiedDealer;
    }

    /**
     * Equality based on user ID (important for security context)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UserPrincipal that))
            return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
