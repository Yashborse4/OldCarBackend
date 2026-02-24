package com.carselling.oldcar.service.moderation;

import com.carselling.oldcar.dto.moderation.ReportMessageRequest;
import com.carselling.oldcar.dto.moderation.MessageReportResponse;
import com.carselling.oldcar.model.ChatMessage;
import com.carselling.oldcar.model.MessageReport;
import com.carselling.oldcar.model.ReportStatus;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.ChatMessageRepository;
import com.carselling.oldcar.repository.MessageReportRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.service.chat.ChatAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageModerationService {

    private final MessageReportRepository messageReportRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatAuthorizationService chatAuthorizationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MessageReport reportMessage(ReportMessageRequest request, Long reporterId) {
        // Validate message exists
        ChatMessage message = chatMessageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        // Validate reporter exists
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporter not found"));

        // Check if user has already reported this message using both message ID and client message ID
        Optional<MessageReport> existingReport = messageReportRepository
                .findByMessageIdAndReporterId(request.getMessageId(), reporterId);
        
        if (existingReport.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reported this message");
        }

        // Additional check: Prevent reporting the same message content from the same user in same chat
        List<MessageReport> similarReports = messageReportRepository.findByReportedMessageId(request.getMessageId());
        if (similarReports.size() >= 5) { // Threshold for automatic moderation
            log.warn("Message {} has been reported {} times, triggering automatic moderation", 
                    request.getMessageId(), similarReports.size());
            // Auto-hide message if it reaches report threshold
            message.setDeleted(true);
            chatMessageRepository.save(message);
        }

        // Create message snapshot
        String messageSnapshot = createMessageSnapshot(message);

        // Create and save report
        MessageReport report = MessageReport.builder()
                .reportedMessage(message)
                .reporter(reporter)
                .reason(request.getReason())
                .additionalComments(request.getAdditionalComments())
                .status(ReportStatus.PENDING)
                .messageSnapshot(messageSnapshot)
                .build();

        return messageReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public Page<MessageReport> getReports(Pageable pageable) {
        return messageReportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<MessageReport> getReportsByStatus(ReportStatus status, Pageable pageable) {
        return messageReportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public MessageReport getReportById(Long reportId) {
        return messageReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    @Transactional
    public MessageReviewResult reviewReport(Long reportId, Long moderatorId, boolean approve, String moderatorNotes) {
        MessageReport report = messageReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        User moderator = userRepository.findById(moderatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Moderator not found"));

        if (report.getStatus() != ReportStatus.PENDING && 
            report.getStatus() != ReportStatus.UNDER_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report is not in a reviewable state");
        }

        if (approve) {
            report.setStatus(ReportStatus.APPROVED);
            // Additional actions for approved reports (e.g., hide message, notify user)
            handleApprovedReport(report);
        } else {
            report.setStatus(ReportStatus.REJECTED);
        }

        report.setModerator(moderator);
        report.setModeratorNotes(moderatorNotes);
        report.setResolvedAt(LocalDateTime.now());

        MessageReport savedReport = messageReportRepository.save(report);
        
        return new MessageReviewResult(savedReport, approve);
    }

    @Transactional(readOnly = true)
    public Long getPendingReportsCount() {
        return messageReportRepository.countByStatus(ReportStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Long getReportsCountAgainstUser(Long userId) {
        return messageReportRepository.countReportsAgainstUser(userId);
    }

    private String createMessageSnapshot(ChatMessage message) {
        try {
            MessageSnapshot snapshot = new MessageSnapshot(
                message.getId(),
                message.getClientMessageId(), // Include client message ID for better tracking
                message.getContent(),
                message.getMessageType().name(),
                message.getSender() != null ? message.getSender().getId() : null,
                message.getSender() != null ? message.getSender().getUsername() : null,
                message.getCreatedAt(),
                message.getChatRoom() != null ? message.getChatRoom().getId() : null
            );
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("Failed to create message snapshot for message {}: {}", message.getId(), e.getMessage());
            return "{}";
        }
    }

    private void handleApprovedReport(MessageReport report) {
        // Implement actions for approved reports:
        // 1. Hide the reported message
        // 2. Notify the message sender
        // 3. Track user violations
        // 4. Potentially ban user if multiple violations
        
        ChatMessage message = report.getReportedMessage();
        message.setDeleted(true);
        chatMessageRepository.save(message);
        
        log.info("Message {} hidden due to approved report {}", message.getId(), report.getId());
    }

    public static class MessageReviewResult {
        private final MessageReport report;
        private final boolean approved;

        public MessageReviewResult(MessageReport report, boolean approved) {
            this.report = report;
            this.approved = approved;
        }

        public MessageReport getReport() { return report; }
        public boolean isApproved() { return approved; }
    }

    private record MessageSnapshot(
        Long messageId,
        String clientMessageId,
        String content,
        String messageType,
        Long senderId,
        String senderUsername,
        LocalDateTime createdAt,
        Long chatRoomId
    ) {}
}