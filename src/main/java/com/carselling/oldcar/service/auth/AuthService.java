package com.carselling.oldcar.service.auth;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.Role;

/**
 * Interface for Authentication Service Operations
 * Adheres to Dependency Inversion Principle (DIP)
 */
public interface AuthService {

    JwtAuthResponse registerUser(RegisterRequest request);

    JwtAuthResponse loginUser(LoginRequest request);

    void requestEmailVerificationOtp(String email);

    JwtAuthResponse verifyEmailOtp(String email, String otp);

    void requestLoginOtp(String email);

    JwtAuthResponse loginWithOtp(String email, String otp);

    JwtAuthResponse refreshToken(RefreshTokenRequest request);

    TokenValidationResponse validateToken(String token);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void logout(String token);

    boolean isUsernameAvailable(String username);

    boolean isEmailAvailable(String email);

    User getCurrentUser();

    User getCurrentUserOrNull();

    boolean hasRole(Role role);

    boolean isOwner(Long userId);
}
