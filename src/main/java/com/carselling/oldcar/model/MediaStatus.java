package com.carselling.oldcar.model;

public enum MediaStatus {
    NONE,
    INIT, // Initial state when car is created but no media yet
    PENDING, // Deprecated: Causing DB constraint issues
    UPLOADING,
    UPLOADED,
    PROCESSING,
    READY,
    COMPLETED, // Final success state
    FAILED,
    DELETED,
    MEDIA_PENDING
}
