package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.MessageReport;
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
public interface MessageReportRepository extends JpaRepository<MessageReport, Long> {

    Page<MessageReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);
    
    Page<MessageReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    List<MessageReport> findByReportedMessageId(Long messageId);
    
    List<MessageReport> findByReporterId(Long reporterId);
    
    Long countByStatus(ReportStatus status);
    
    @Query("SELECT COUNT(r) FROM MessageReport r WHERE r.reportedMessage.sender.id = :userId")
    Long countReportsAgainstUser(@Param("userId") Long userId);
    
    @Query("SELECT r FROM MessageReport r WHERE r.reportedMessage.id = :messageId AND r.reporter.id = :reporterId")
    Optional<MessageReport> findByMessageIdAndReporterId(@Param("messageId") Long messageId, 
                                                        @Param("reporterId") Long reporterId);
    
    @Query("SELECT r FROM MessageReport r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<MessageReport> findPendingReports(Pageable pageable);
}