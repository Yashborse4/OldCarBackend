package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
    
    List<WishlistItem> findByUserId(Long userId);
    
    boolean existsByUserIdAndCarId(Long userId, Long carId);
    
    void deleteByUserIdAndCarId(Long userId, Long carId);
}
