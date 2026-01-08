package com.carselling.oldcar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for system statistics
 * Contains user and car statistics for administrative dashboards
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemStatistics {

    private UserStatistics userStatistics;
    private CarStatistics carStatistics;

    public UserStatistics getUserStatistics() {
        return userStatistics;
    }

    public void setUserStatistics(UserStatistics userStatistics) {
        this.userStatistics = userStatistics;
    }

    public CarStatistics getCarStatistics() {
        return carStatistics;
    }

    public void setCarStatistics(CarStatistics carStatistics) {
        this.carStatistics = carStatistics;
    }
}
