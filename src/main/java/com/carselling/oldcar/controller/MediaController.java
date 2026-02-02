package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.MediaService;
import com.carselling.oldcar.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * Controller for Media Access.
 * Separates access concerns from management (FileController).
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Media Access", description = "Endpoints for accessing public and private media")
public class MediaController {

    private final MediaService mediaService;

    /**
     * Get Media (Redirects to secure or public URL)
     * Handles Access Checks.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()") // Or permitAll() if handling public logic inside?
    // Ideally we want permitAll() so public files can be accessed by guest users if
    // needed.
    // BUT we need userId for private checks.
    // Solution: PreAuthorize("isAuthenticated()") for everything since 'Private
    // dealer/user media' implies logged in.
    // Public Car Images are usually accessed via direct CDN URL stored in DB car
    // entity.
    // This endpoint is primarily for "smart" access to potentially private docs.
    @Operation(summary = "Get media file", description = "Redirects to the actual file URL with access checks")
    public void getMedia(
            @PathVariable Long id,
            HttpServletResponse response) throws IOException {

        Long userId = SecurityUtils.getCurrentUserId();
        String url = mediaService.getMediaFileUrl(id, userId);

        response.sendRedirect(url);
    }

    /**
     * Get Media Details
     */
    @GetMapping("/{id}/details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMediaDetails(@PathVariable Long id) {
        // This would fetch metadata by ID. Current usage in FileController is by URL.
        // We might need to add getFileMetadataById to Service.
        // Skipping for now to focus on Access.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
