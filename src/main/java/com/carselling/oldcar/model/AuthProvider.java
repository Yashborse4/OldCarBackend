package com.carselling.oldcar.model;

/**
 * Enumeration of supported authentication providers
 */
public enum AuthProvider {
    LOCAL,     // Standard email/password
    GOOGLE,    // Google Social Login
    PHONE,     // Phone/OTP login
    FACEBOOK,  // Future expansion
    APPLE      // Future expansion
}
