package com.carselling.oldcar.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Utility for handling Pagination and Sorting consistently across Controllers
 */
public class PageableUtils {

    private PageableUtils() {
        // Private constructor to hide the implicit one
    }

    /**
     * Create Pageable from request parameters with safe defaults
     *
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param sort Sort string (e.g., "createdAt,desc")
     * @return Pageable object
     */
    public static Pageable createPageable(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        String sortDirection = sortParams.length > 1 ? sortParams[1] : "desc";

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // Cap size to prevent DOS
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);

        return PageRequest.of(safePage, safeSize, Sort.by(direction, sortField));
    }

    /**
     * Create simple Pageable with default sort
     */
    public static Pageable createPageable(int page, int size) {
        return createPageable(page, size, "createdAt,desc");
    }
}
