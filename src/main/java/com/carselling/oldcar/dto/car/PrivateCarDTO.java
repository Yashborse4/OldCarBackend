package com.carselling.oldcar.dto.car;

import com.carselling.oldcar.dto.user.UserSummary;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Private Car DTO - Contains full car information including seller details
 * Requires authentication
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PrivateCarDTO extends PublicCarDTO {
    private String description;
    private List<String> images;
    private Integer numberOfOwners;
    private String color;
    private UserSummary owner;
    private String sellerPhone;
    private Boolean isFeatured;
    private Boolean isSold;
    private Long viewCount;
    private String location; // Full location details
    private Boolean isApproved;
    private LocalDateTime updatedAt;
}
