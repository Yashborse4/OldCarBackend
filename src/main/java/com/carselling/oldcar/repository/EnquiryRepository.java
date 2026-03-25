package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.Enquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    Page<Enquiry> findByCarOwnerId(Long ownerId, Pageable pageable);

    Page<Enquiry> findByUserId(Long userId, Pageable pageable);

    List<Enquiry> findByCarIdAndUserId(Long carId, Long userId);

    long countByStatus(Enquiry.EnquiryStatus status);

    long countByCarOwnerIdAndStatus(Long ownerId, Enquiry.EnquiryStatus status);
}
