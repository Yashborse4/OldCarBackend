package com.carselling.oldcar.model;

/**
 * User roles with different permission levels
 * VIEWER: Can only view cars (read-only access)
 * SELLER: Can create, update, delete own cars
 * DEALER: Enhanced permissions including car featuring and analytics
 * ADMIN: Full system access including user management
 */
public enum Role {
    VIEWER("VIEWER", "Read-only access to car listings"),
    SELLER("SELLER", "Can manage own car listings"),
    DEALER("DEALER", "Enhanced permissions for dealers"),
    ADMIN("ADMIN", "Full administrative access");

    private final String name;
    private final String description;

    Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static Role fromString(String role) {
        if (role == null) {
            return VIEWER; // Default role
        }
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return VIEWER; // Default to VIEWER for invalid roles
        }
    }

    /**
     * Check if this role has permission to perform an action
     */
    public boolean hasPermission(String permission) {
        return switch (this) {
            case ADMIN -> true; // Admin has all permissions
            case DEALER -> switch (permission) {
                case "car:read", "car:create", "car:update:own", "car:delete:own", 
                     "car:feature", "analytics:view" -> true;
                default -> false;
            };
            case SELLER -> switch (permission) {
                case "car:read", "car:create", "car:update:own", "car:delete:own" -> true;
                default -> false;
            };
            case VIEWER -> "car:read".equals(permission);
        };
    }
}
