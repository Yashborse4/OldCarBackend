package com.carselling.oldcar.dto.moderation;

import com.carselling.oldcar.model.MessageReport;
import com.carselling.oldcar.model.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReportResponse {

    private Long id;
    private Long reportedMessageId;
    private Long reporterId;
    private String reporterName;
    private MessageReport.ReportReason reason;
    private String additionalComments;
    private ReportStatus status;
    private String moderatorNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String messageSnapshot;
}