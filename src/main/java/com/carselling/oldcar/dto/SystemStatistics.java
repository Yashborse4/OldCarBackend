package com.carselling.oldcar.dto;

import com.carselling.oldcar.service.UserService;
import com.carselling.oldcar.service.CarService;
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
    
    private UserService.UserStatistics userStatistics;
    private CarService.CarStatistics carStatistics;
    
    public UserService.UserStatistics getUserStatistics() {
        return userStatistics;
    }
    
    public void setUserStatistics(UserService.UserStatistics userStatistics) {
        this.userStatistics = userStatistics;
    }
    
    public CarService.CarStatistics getCarStatistics() {
        return carStatistics;
    }
    
    public void setCarStatistics(CarService.CarStatistics carStatistics) {
        this.carStatistics = carStatistics;
    }
}
