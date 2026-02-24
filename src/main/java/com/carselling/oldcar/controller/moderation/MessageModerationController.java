package com.carselling.oldcar.controller.moderation;

import com.carselling.oldcar.dto.moderation.ReportMessageRequest;
import com.carselling.oldcar.dto.moderation.MessageReportResponse;
import com.carselling.oldcar.model.MessageReport;
import com.carselling.oldcar.model.ReportStatus;
import com.carselling.oldcar.service.moderation.MessageModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Message Moderation", description = "Endpoints for reporting and moderating chat messages")
public class MessageModerationController {

    private final MessageModerationService messageModerationService;

    @PostMapping("/report")
    @Operation(summary = "Report a message", description = "Report a chat message for moderation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Message reported successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Message not found"),
        @ApiResponse(responseCode = "409", description = "Already reported this message")
    })
    public ResponseEntity<com.carselling.oldcar.dto.common.ApiResponse<MessageReportResponse>> reportMessage(
            @Valid @RequestBody ReportMessageRequest request,
            @AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
        
        log.info("User {} reporting message {}", currentUser.getId(), request.getMessageId());
        
        MessageReport report = messageModerationService.reportMessage(request, currentUser.getId());
        MessageReportResponse response = convertToResponse(report);
        
        return ResponseEntity.ok(com.carselling.oldcar.dto.common.ApiResponse.success("Message reported successfully", response));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    @Operation(summary = "Get all reports", description = "Get paginated list of all message reports (Admin/Moderator only)")
    public ResponseEntity<com.carselling.oldcar.dto.common.ApiResponse<Page<MessageReportResponse>>> getAllReports(Pageable pageable) {
        Page<MessageReport> reports = messageModerationService.getReports(pageable);
        Page<MessageReportResponse> response = reports.map(this::convertToResponse);
        
        return ResponseEntity.ok(com.carselling.oldcar.dto.common.ApiResponse.success("Reports retrieved successfully", response));
    }

    @GetMapping("/reports/status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    @Operation(summary = "Get reports by status", description = "Get paginated list of reports filtered by status")
    public ResponseEntity<com.carselling.oldcar.dto.common.ApiResponse<Page<MessageReportResponse>>> getReportsByStatus(
            @PathVariable ReportStatus status,
            Pageable pageable) {
        
        Page<MessageReport> reports = messageModerationService.getReportsByStatus(status, pageable);
        Page<MessageReportResponse> response = reports.map(this::convertToResponse);
        
        return ResponseEntity.ok(com.carselling.oldcar.dto.common.ApiResponse.success("Reports retrieved successfully", response));
    }

    @GetMapping("/reports/{reportId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    @Operation(summary = "Get report by ID", description = "Get detailed information about a specific report")
    public ResponseEntity<com.carselling.oldcar.dto.common.ApiResponse<MessageReportResponse>> getReport(@PathVariable Long reportId) {
        MessageReport report = messageModerationService.getReportById(reportId);
        MessageReportResponse response = convertToResponse(report);
        
        return ResponseEntity.ok(com.carselling.oldcar.dto.common.ApiResponse.success("Report retrieved successfully", response));
    }

    @PostMapping("/reports/{reportId}/review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    @Operation(summary = "Review a report", description = "Review and take action on a reported message")
    public ResponseEntity<com.carselling.oldcar.dto.common.ApiResponse<Map<String, Object>>> reviewReport(
            @PathVariable Long reportId,
            @RequestParam boolean approve,
            @RequestParam(required = false) String moderatorNotes,
            @AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
        
        MessageModerationService.MessageReviewResult result = messageModerationService
                .reviewReport(reportId, currentUser.getId(), approve, moderatorNotes);
        
        String message = approve ? "Report approved and message hidden" : "Report rejected";
        
        return ResponseEntity.ok(com.carselling.oldcar.dto.common.ApiResponse.success(message, Map.of(
            "report", convertToResponse(result.getReport()),
            "actionTaken", approve ? "MESSAGE_HIDDEN" : "REPORT_REJECTED"
        )));
    }

    @GetMapping("/stats/pending-count")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    @Operation(summary = "Get pending reports count", description = "Get count of pending reports")
    public ResponseEntity<com.carselling.oldcar.dto.common.ApiResponse<Map<String, Long>>> getPendingReportsCount() {
        Long count = messageModerationService.getPendingReportsCount();
        return ResponseEntity.ok(com.carselling.oldcar.dto.common.ApiResponse.success("Pending reports count", Map.of("pendingCount", count)));
    }

    private MessageReportResponse convertToResponse(MessageReport report) {
        return MessageReportResponse.builder()
                .id(report.getId())
                .reportedMessageId(report.getReportedMessage().getId())
                .reporterId(report.getReporter().getId())
                .reporterName(report.getReporter().getUsername())
                .reason(report.getReason())
                .additionalComments(report.getAdditionalComments())
                .status(report.getStatus())
                .moderatorNotes(report.getModeratorNotes())
                .createdAt(report.getCreatedAt())
                .resolvedAt(report.getResolvedAt())
                .messageSnapshot(report.getMessageSnapshot())
                .build();
    }
}