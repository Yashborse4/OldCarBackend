package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.DealerVerificationRequest;
import com.carselling.oldcar.model.DealerVerificationRequest.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for DealerVerificationRequest entity
 */
@Repository
public interface DealerVerificationRepository extends JpaRepository<DealerVerificationRequest, Long> {

    /**
     * Find all verification requests by dealer ID
     */
    Page<DealerVerificationRequest> findByDealerId(Long dealerId, Pageable pageable);

    /**
     * Find the latest verification request by dealer ID
     */
    @Query("SELECT r FROM DealerVerificationRequest r WHERE r.dealer.id = :dealerId ORDER BY r.submittedAt DESC LIMIT 1")
    Optional<DealerVerificationRequest> findLatestByDealerId(@Param("dealerId") Long dealerId);

    /**
     * Find all verification requests by status
     */
    Page<DealerVerificationRequest> findByStatus(VerificationStatus status, Pageable pageable);

    /**
     * Count pending verification requests
     */
    long countByStatus(VerificationStatus status);

    /**
     * Check if dealer has any pending request
     */
    boolean existsByDealerIdAndStatus(Long dealerId, VerificationStatus status);
}
