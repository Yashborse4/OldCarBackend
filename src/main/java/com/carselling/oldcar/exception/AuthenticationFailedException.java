package com.carselling.oldcar.exception;

import java.util.Map;

/**
 * Exception thrown when authentication fails
 */
public class AuthenticationFailedException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> data;

    public enum AuthError {
        USER_NOT_FOUND("USER_NOT_FOUND"),
        INVALID_CREDENTIALS("INVALID_CREDENTIALS"),
        ACCOUNT_LOCKED("ACCOUNT_LOCKED"),
        EMAIL_NOT_VERIFIED("EMAIL_NOT_VERIFIED"),
        INVALID_OTP("INVALID_OTP"),
        TOKEN_EXPIRED("TOKEN_EXPIRED"),
        TOKEN_INVALID("TOKEN_INVALID"),
        AUTH_FAILED("AUTH_FAILED");

        private final String code;

        AuthError(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public AuthenticationFailedException(String message) {
        super(message);
        this.errorCode = AuthError.AUTH_FAILED.getCode();
        this.data = null;
    }

    public AuthenticationFailedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }

    public AuthenticationFailedException(String message, AuthError error) {
        super(message);
        this.errorCode = error.getCode();
        this.data = null;
    }

    public AuthenticationFailedException(String message, AuthError error, Map<String, Object> data) {
        super(message);
        this.errorCode = error.getCode();
        this.data = data;
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = AuthError.AUTH_FAILED.getCode();
        this.data = null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
