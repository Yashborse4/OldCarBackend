package com.carselling.oldcar.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String lastViewedAt; // Formatted date or relative time could be good, but for now simple count is
                                 // key.
}
