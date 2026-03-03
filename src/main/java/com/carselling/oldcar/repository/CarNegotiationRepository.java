package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarNegotiation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarNegotiationRepository extends JpaRepository<CarNegotiation, Long> {
    
    List<CarNegotiation> findByCarId(Long carId);
    
    List<CarNegotiation> findByBuyerId(Long buyerId);
    
    List<CarNegotiation> findBySellerId(Long sellerId);
    
    List<CarNegotiation> findByBuyerIdOrSellerId(Long buyerId, Long sellerId);
    
    List<CarNegotiation> findByStatus(String status);
}
