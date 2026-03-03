package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarTestDrive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarTestDriveRepository extends JpaRepository<CarTestDrive, Long> {
    
    List<CarTestDrive> findByUserId(Long userId);
    
    List<CarTestDrive> findByCarId(Long carId);
    
    List<CarTestDrive> findByUserIdAndStatus(Long userId, String status);
}
