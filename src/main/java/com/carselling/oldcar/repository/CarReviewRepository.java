package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarReviewRepository extends JpaRepository<CarReview, Long> {
    
    List<CarReview> findByCarId(Long carId);
    
    List<CarReview> findByUserId(Long userId);
    
    @Query("SELECT AVG(r.rating) FROM CarReview r WHERE r.car.id = :carId")
    Double findAverageRatingByCarId(Long carId);
    
    @Query("SELECT COUNT(r) FROM CarReview r WHERE r.car.id = :carId")
    Long countReviewsByCarId(Long carId);
}
