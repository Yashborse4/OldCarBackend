package com.carselling.oldcar.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a user who viewed a car, including user and car details.
 * Used for dealer analytics and lead generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarViewerDto {
    private Long userId;
    private String userName;
    private String userEmail;
    private String userProfileImage;
    private String carId;
    private String carMake;
    private String carModel;
    private Integer carYear;
    private String carImage;
    private Long carPrice;
    private Long viewCount;
    private String lastViewedAt;
}
