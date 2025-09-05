package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.ChatRoomV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ChatRoomV2 entity with enhanced query methods
 */
@Repository
public interface ChatRoomV2Repository extends JpaRepository<ChatRoomV2, Long> {

    /**
     * Find chat rooms where user is a participant
     */
    @Query("SELECT DISTINCT cr FROM ChatRoomV2 cr " +
           "JOIN cr.participants p " +
           "WHERE p.user.id = :userId AND p.isActive = true " +
           "ORDER BY cr.lastActivityAt DESC")
    Page<ChatRoomV2> findByParticipantUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find private chat between two users
     */
    @Query("SELECT cr FROM ChatRoomV2 cr " +
           "JOIN cr.participants p1 " +
           "JOIN cr.participants p2 " +
           "WHERE cr.type = 'PRIVATE' " +
           "AND p1.user.id = :userId1 AND p1.isActive = true " +
           "AND p2.user.id = :userId2 AND p2.isActive = true")
    Optional<ChatRoomV2> findPrivateChatBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Find car inquiry chat for specific car between buyer and seller
     */
    @Query("SELECT cr FROM ChatRoomV2 cr " +
           "JOIN cr.participants p1 " +
           "JOIN cr.participants p2 " +
           "WHERE cr.type = 'CAR_INQUIRY' " +
           "AND cr.car.id = :carId " +
           "AND p1.user.id = :buyerId AND p1.isActive = true " +
           "AND p2.user.id = :sellerId AND p2.isActive = true")
    Optional<ChatRoomV2> findCarInquiryChat(@Param("carId") Long carId, @Param("buyerId") Long buyerId, @Param("sellerId") Long sellerId);

    /**
     * Find group chats by type
     */
    @Query("SELECT cr FROM ChatRoomV2 cr " +
           "WHERE cr.type = :chatType " +
           "AND cr.isActive = true " +
           "ORDER BY cr.lastActivityAt DESC")
    Page<ChatRoomV2> findByTypeAndIsActiveTrue(@Param("chatType") ChatRoomV2.ChatType chatType, Pageable pageable);

    /**
     * Find dealer-only groups where user is a participant
     */
    @Query("SELECT DISTINCT cr FROM ChatRoomV2 cr " +
           "JOIN cr.participants p " +
           "JOIN p.user u " +
           "WHERE cr.type = 'DEALER_ONLY' " +
           "AND p.user.id = :userId AND p.isActive = true " +
           "AND u.role = 'DEALER' " +
           "ORDER BY cr.lastActivityAt DESC")
    Page<ChatRoomV2> findDealerGroupsByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Count active chat rooms for user
     */
    @Query("SELECT COUNT(DISTINCT cr) FROM ChatRoomV2 cr " +
           "JOIN cr.participants p " +
           "WHERE p.user.id = :userId AND p.isActive = true AND cr.isActive = true")
    long countActiveByUserId(@Param("userId") Long userId);

    /**
     * Find chat rooms by car ID
     */
    @Query("SELECT cr FROM ChatRoomV2 cr " +
           "WHERE cr.car.id = :carId " +
           "AND cr.isActive = true " +
           "ORDER BY cr.createdAt DESC")
    Page<ChatRoomV2> findByCarIdAndIsActiveTrue(@Param("carId") Long carId, Pageable pageable);

    /**
     * Search chat rooms by name or description
     */
    @Query("SELECT DISTINCT cr FROM ChatRoomV2 cr " +
           "JOIN cr.participants p " +
           "WHERE p.user.id = :userId AND p.isActive = true " +
           "AND (LOWER(cr.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(cr.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY cr.lastActivityAt DESC")
    Page<ChatRoomV2> searchByNameOrDescription(@Param("userId") Long userId, @Param("query") String query, Pageable pageable);
}
