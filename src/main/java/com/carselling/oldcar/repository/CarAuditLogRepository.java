package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarAuditLogRepository extends JpaRepository<CarAuditLog, Long> {
    List<CarAuditLog> findByCarIdOrderByChangedAtDesc(Long carId);
}
