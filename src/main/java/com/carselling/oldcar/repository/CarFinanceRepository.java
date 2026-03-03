package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarFinance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarFinanceRepository extends JpaRepository<CarFinance, Long> {
    
    List<CarFinance> findByUserId(Long userId);
    
    List<CarFinance> findByCarId(Long carId);
    
    List<CarFinance> findByUserIdAndStatus(Long userId, String status);
}
