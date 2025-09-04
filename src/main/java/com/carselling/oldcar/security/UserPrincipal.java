package com.carselling.oldcar.security;

import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User Principal for enhanced Spring Security user details
 * Provides comprehensive user information for authentication and authorization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    
    private Long id;
    private String username;
    private String email;
    private String password;
    private Role role;
    private String location;
    private Boolean isActive;
    private Boolean isEmailVerified;
    private Integer failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = createAuthorities(user.getRole());
        
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.getRole(),
                user.getLocation(),
                user.getIsActive(),
                user.getIsEmailVerified(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                authorities
        );
    }

    private static List<GrantedAuthority> createAuthorities(Role role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Add role-based authority
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        
        // Add permission-based authorities
        switch (role) {
            case ADMIN -> {
                authorities.add(new SimpleGrantedAuthority("car:read"));
                authorities.add(new SimpleGrantedAuthority("car:create"));
                authorities.add(new SimpleGrantedAuthority("car:update:any"));
                authorities.add(new SimpleGrantedAuthority("car:delete:any"));
                authorities.add(new SimpleGrantedAuthority("car:feature"));
                authorities.add(new SimpleGrantedAuthority("user:manage"));
                authorities.add(new SimpleGrantedAuthority("analytics:view"));
            }
            case DEALER -> {
                authorities.add(new SimpleGrantedAuthority("car:read"));
                authorities.add(new SimpleGrantedAuthority("car:create"));
                authorities.add(new SimpleGrantedAuthority("car:update:own"));
                authorities.add(new SimpleGrantedAuthority("car:delete:own"));
                authorities.add(new SimpleGrantedAuthority("car:feature"));
                authorities.add(new SimpleGrantedAuthority("analytics:view"));
            }
            case SELLER -> {
                authorities.add(new SimpleGrantedAuthority("car:read"));
                authorities.add(new SimpleGrantedAuthority("car:create"));
                authorities.add(new SimpleGrantedAuthority("car:update:own"));
                authorities.add(new SimpleGrantedAuthority("car:delete:own"));
            }
            case VIEWER -> {
                authorities.add(new SimpleGrantedAuthority("car:read"));
            }
        }
        
        return authorities;
    }

    // UserDetails interface methods
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // We don't implement account expiration
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // We don't implement password expiration
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(isActive);
    }

    // Helper methods
    public boolean hasRole(Role role) {
        return this.role == role;
    }

    public boolean hasAuthority(String authority) {
        return authorities.stream()
                .anyMatch(grantedAuth -> grantedAuth.getAuthority().equals(authority));
    }

    public boolean hasAnyRole(Role... roles) {
        for (Role role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyAuthority(String... authorities) {
        for (String authority : authorities) {
            if (hasAuthority(authority)) {
                return true;
            }
        }
        return false;
    }

    public String getDisplayName() {
        return username;
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    public boolean isDealer() {
        return hasRole(Role.DEALER);
    }

    public boolean isSeller() {
        return hasRole(Role.SELLER);
    }

    public boolean isViewer() {
        return hasRole(Role.VIEWER);
    }

    public boolean canManageUsers() {
        return hasAuthority("user:manage");
    }

    public boolean canFeatureCars() {
        return hasAuthority("car:feature");
    }

    public boolean canViewAnalytics() {
        return hasAuthority("analytics:view");
    }

    // Security override - exclude password from toString
    @Override
    public String toString() {
        return "UserPrincipal{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", location='" + location + '\'' +
                ", isActive=" + isActive +
                ", authorities=" + authorities +
                '}';
    }
}
