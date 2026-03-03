package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarDocumentRepository extends JpaRepository<CarDocument, Long> {
    
    List<CarDocument> findByCarId(Long carId);
    
    List<CarDocument> findByUserId(Long userId);
    
    List<CarDocument> findByCarIdAndDocumentType(Long carId, String documentType);
    
    List<CarDocument> findByIsVerified(Boolean isVerified);
}
