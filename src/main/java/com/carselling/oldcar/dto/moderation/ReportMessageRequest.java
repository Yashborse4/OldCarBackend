package com.carselling.oldcar.dto.moderation;

import com.carselling.oldcar.model.MessageReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportMessageRequest {

    @NotNull(message = "Message ID is required")
    private Long messageId;

    @NotNull(message = "Report reason is required")
    private MessageReport.ReportReason reason;

    private String additionalComments;
}