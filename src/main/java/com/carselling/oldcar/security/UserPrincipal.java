package com.carselling.oldcar.security;

import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPrincipal implements UserDetails {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("email")
    private String email;

    @JsonProperty("password")
    private String password;

    @JsonProperty("role")
    private Role role;

    @JsonProperty("active")
    private boolean active;

    @JsonProperty("emailVerified")
    private boolean emailVerified;

    @JsonProperty("verifiedDealer")
    private boolean verifiedDealer;

    @JsonProperty("lockedUntil")
    private LocalDateTime lockedUntil;


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
        if (verifiedDealer && role != Role.DEALER && role != Role.ADMIN) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_" + role.name()),
                    new SimpleGrantedAuthority("ROLE_DEALER"));
        }
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
    // Lombok @Getter provides these. Retaining only non-standard ones if any.

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
