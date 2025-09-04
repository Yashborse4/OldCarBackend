package com.carselling.oldcar.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for date and time operations
 */
public class DateUtils {

    public static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DEFAULT_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter ISO_DATETIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private DateUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Get current date time
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Format LocalDateTime to string with default format
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime.format(DEFAULT_DATETIME_FORMAT);
    }

    /**
     * Format LocalDateTime to string with custom format
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        return dateTime.format(formatter);
    }

    /**
     * Check if a date is within the last N days
     */
    public static boolean isWithinLastDays(LocalDateTime dateTime, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return dateTime.isAfter(cutoff);
    }

    /**
     * Check if a date is within the last N hours
     */
    public static boolean isWithinLastHours(LocalDateTime dateTime, int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        return dateTime.isAfter(cutoff);
    }

    /**
     * Get the start of day for a given date
     */
    public static LocalDateTime getStartOfDay(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Get the end of day for a given date
     */
    public static LocalDateTime getEndOfDay(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusNanos(1);
    }

    /**
     * Calculate time difference in a human-readable format
     */
    public static String getTimeAgo(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        
        long minutes = ChronoUnit.MINUTES.between(dateTime, now);
        long hours = ChronoUnit.HOURS.between(dateTime, now);
        long days = ChronoUnit.DAYS.between(dateTime, now);
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (minutes < 1) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes != 1 ? "s" : "") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        } else if (days < 7) {
            return days + " day" + (days != 1 ? "s" : "") + " ago";
        } else if (weeks < 4) {
            return weeks + " week" + (weeks != 1 ? "s" : "") + " ago";
        } else if (months < 12) {
            return months + " month" + (months != 1 ? "s" : "") + " ago";
        } else {
            return years + " year" + (years != 1 ? "s" : "") + " ago";
        }
    }

    /**
     * Convert LocalDateTime to epoch milliseconds
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Create LocalDateTime from epoch milliseconds
     */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMilli), 
                ZoneId.systemDefault()
        );
    }
}
