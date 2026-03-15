package com.carselling.oldcar.security;

import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Robust UserPrincipal for Spring Security and Redis Caching.
 * 
 * Uses an immutable pattern with a single @JsonCreator constructor to ensure
 * reliable serialization/deserialization across different Jackson versions
 * and caching providers.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String email;

    @JsonIgnore
    private String password;

    private Role role;
    private boolean active;
    private boolean emailVerified;
    private boolean verifiedDealer;
    private LocalDateTime lockedUntil;

    /**
     * Default constructor required by Jackson for Redis deserialization
     */
    protected UserPrincipal() {
    }

    /**
     * Primary constructor for Jackson deserialization and internal use.
     */
    @JsonCreator
    public UserPrincipal(
            @JsonProperty("id") Long id,
            @JsonProperty("email") String email,
            @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY) String password,
            @JsonProperty("role") Role role,
            @JsonProperty("active") boolean active,
            @JsonProperty("emailVerified") boolean emailVerified,
            @JsonProperty("verifiedDealer") boolean verifiedDealer,
            @JsonProperty("lockedUntil") LocalDateTime lockedUntil) {
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
                user.isDealerVerified(),
                user.getLockedUntil());
    }

    /**
     * Authorities used by Spring Security
     */
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = "ROLE_" + role.name();
        
        // Only add ROLE_DEALER if they are a verified dealer AND 
        // their main role isn't already DEALER or ADMIN
        if (verifiedDealer && role != Role.DEALER && role != Role.ADMIN) {
            return List.of(new SimpleGrantedAuthority(roleName), new SimpleGrantedAuthority("ROLE_DEALER"));
        }
        
        return List.of(new SimpleGrantedAuthority(roleName));
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return email;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return active && emailVerified;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPrincipal that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
