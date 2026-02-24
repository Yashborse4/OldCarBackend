package com.carselling.oldcar.dto.moderation;

import com.carselling.oldcar.model.CarReport;
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
public class CarReportResponse {

    private Long id;
    private String reportedCarId;
    private String reportedCarTitle;
    private Long reporterId;
    private String reporterName;
    private CarReport.CarReportReason reason;
    private String additionalComments;
    private ReportStatus status;
    private String moderatorNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String carSnapshot;
}