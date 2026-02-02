package com.carselling.oldcar.model;

/**
 * Media processing status for car listings.
 * 
 * State Flow:
 * NONE -> INIT -> UPLOADING -> UPLOADED -> PROCESSING -> READY (publicly
 * visible)
 * -> FAILED (retry or manual intervention)
 * -> DELETED (media removed)
 * 
 * READY is the single state that indicates media is complete and car can be
 * publicly visible.
 */
public enum MediaStatus {
    NONE, // No media state set
    INIT, // Initial state when car is created but no media yet
    UPLOADING, // Media upload in progress
    UPLOADED, // Media uploaded, awaiting processing
    PROCESSING, // Media being processed (e.g., transcoding, optimization)
    READY, // Final success state - car is publicly visible
    FAILED, // Processing failed
    DELETED // Media has been deleted
}
