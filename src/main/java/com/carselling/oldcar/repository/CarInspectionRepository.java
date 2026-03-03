package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarInspectionRepository extends JpaRepository<CarInspection, Long> {
    
    List<CarInspection> findByUserId(Long userId);
    
    List<CarInspection> findByCarId(Long carId);
    
    List<CarInspection> findByUserIdAndStatus(Long userId, String status);
}
