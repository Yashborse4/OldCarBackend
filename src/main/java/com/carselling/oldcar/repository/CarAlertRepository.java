package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarAlertRepository extends JpaRepository<CarAlert, Long> {
    
    List<CarAlert> findByUserId(Long userId);
    
    List<CarAlert> findByUserIdAndIsActive(Long userId, Boolean isActive);
}
