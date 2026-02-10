package com.carselling.oldcar.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadDto {
    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl;

    // Most interested car details
    private Long carId;
    private String carTitle;
    private String carImage;
    private String carPrice;

    private Long viewCount;
    private Long interactionCount; // Inquiries, calls, etc.
    private String lastActiveAt;

    // Lead Score or Status
    private Double interestScore; // calculated based on views/interactions
}
