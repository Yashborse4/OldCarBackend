package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarReport;
import com.carselling.oldcar.model.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarReportRepository extends JpaRepository<CarReport, Long> {
    
    Page<CarReport> findByStatus(ReportStatus status, Pageable pageable);
    
    Page<CarReport> findByReportedCarId(String carId, Pageable pageable);
    
    Page<CarReport> findByReporterId(Long reporterId, Pageable pageable);
    
    @Query("SELECT cr FROM CarReport cr WHERE cr.reportedCar.id = :carId AND cr.reporter.id = :reporterId")
    Optional<CarReport> findByCarIdAndReporterId(@Param("carId") String carId, @Param("reporterId") Long reporterId);
    
    @Query("SELECT COUNT(cr) FROM CarReport cr WHERE cr.reportedCar.id = :carId AND cr.status = 'PENDING'")
    Long countPendingReportsByCarId(@Param("carId") String carId);
    
    @Query("SELECT cr FROM CarReport cr WHERE cr.reportedCar.id = :carId ORDER BY cr.createdAt DESC")
    List<CarReport> findAllByCarId(@Param("carId") String carId);
}