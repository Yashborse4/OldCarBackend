package com.carselling.oldcar.model;

/**
 * Represents the lifecycle status of a Car listing.
 */
public enum CarStatus {
    DRAFT, // Created but not yet visible to public (incomplete or waiting for user)
    PUBLISHED, // Visible to public
    SOLD, // Sold via the platform or externally
    RESERVED, // Temporarily held
    ARCHIVED, // Hidden by owner
    DELETED, // Soft deleted
    PROCESSING, // System is processing (e.g., media upload) - though MediaStatus handles
                // specific media
    PENDING_VERIFICATION // Waiting for dealer verification
}
