package com.carselling.oldcar.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtil {

    private PaginationUtil() {
    }

    public static Pageable createPageable(int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;

        Sort.Direction sortDirection;
        if ("asc".equalsIgnoreCase(direction)) {
            sortDirection = Sort.Direction.ASC;
        } else {
            sortDirection = Sort.Direction.DESC;
        }

        Sort sortObj;
        if (sort == null || sort.isBlank()) {
            sortObj = Sort.unsorted();
        } else {
            sortObj = Sort.by(sortDirection, sort);
        }

        return PageRequest.of(safePage, safeSize, sortObj);
    }
}

