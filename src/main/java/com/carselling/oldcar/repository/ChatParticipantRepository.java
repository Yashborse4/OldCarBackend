package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.ChatParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ChatParticipant entity with enhanced query methods
 */
@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    /**
     * Find all active participants in a chat room
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.isActive = true " +
           "ORDER BY p.joinedAt ASC")
    List<ChatParticipant> findActiveByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find all participants (active and inactive) in a chat room
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "ORDER BY p.joinedAt ASC")
    List<ChatParticipant> findAllByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find participant by user and chat room
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.user.id = :userId " +
           "AND p.chatRoom.id = :chatRoomId")
    Optional<ChatParticipant> findByUserIdAndChatRoomId(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId);

    /**
     * Find all chat rooms where user is an active participant
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.user.id = :userId " +
           "AND p.isActive = true " +
           "ORDER BY p.lastSeenAt DESC")
    List<ChatParticipant> findActiveByUserId(@Param("userId") Long userId);

    /**
     * Find all chat rooms where user is a participant (active and inactive)
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.user.id = :userId " +
           "ORDER BY p.lastSeenAt DESC")
    List<ChatParticipant> findAllByUserId(@Param("userId") Long userId);

    /**
     * Count active participants in a chat room
     */
    @Query("SELECT COUNT(p) FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.isActive = true")
    long countActiveByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find participants with admin role in a chat room
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.role = 'ADMIN' " +
           "AND p.isActive = true")
    List<ChatParticipant> findAdminsByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * Check if user is an admin in a chat room
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM ChatParticipant p " +
           "WHERE p.user.id = :userId " +
           "AND p.chatRoom.id = :chatRoomId " +
           "AND p.role = 'ADMIN' " +
           "AND p.isActive = true")
    boolean isUserAdminInChatRoom(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId);

    /**
     * Check if user is an active participant in a chat room
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM ChatParticipant p " +
           "WHERE p.user.id = :userId " +
           "AND p.chatRoom.id = :chatRoomId " +
           "AND p.isActive = true")
    boolean isUserActiveParticipant(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId);

    /**
     * Update participant's last seen timestamp
     */
    @Modifying
    @Query("UPDATE ChatParticipant p SET p.lastSeenAt = :lastSeenAt " +
           "WHERE p.user.id = :userId AND p.chatRoom.id = :chatRoomId")
    int updateLastSeenAt(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    /**
     * Deactivate participant (remove from chat room)
     */
    @Modifying
    @Query("UPDATE ChatParticipant p SET p.isActive = false, p.leftAt = :leftAt " +
           "WHERE p.user.id = :userId AND p.chatRoom.id = :chatRoomId")
    int deactivateParticipant(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId, @Param("leftAt") LocalDateTime leftAt);

    /**
     * Reactivate participant (rejoin chat room)
     */
    @Modifying
    @Query("UPDATE ChatParticipant p SET p.isActive = true, p.leftAt = null " +
           "WHERE p.user.id = :userId AND p.chatRoom.id = :chatRoomId")
    int reactivateParticipant(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId);

    /**
     * Update participant role
     */
    @Modifying
    @Query("UPDATE ChatParticipant p SET p.role = :role " +
           "WHERE p.user.id = :userId AND p.chatRoom.id = :chatRoomId")
    int updateParticipantRole(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId, @Param("role") ChatParticipant.ParticipantRole role);

    /**
     * Find participants who haven't been seen since a specific time
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.isActive = true " +
           "AND p.lastSeenAt < :cutoffTime " +
           "ORDER BY p.lastSeenAt ASC")
    List<ChatParticipant> findInactiveParticipants(@Param("chatRoomId") Long chatRoomId, @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find participants with specific role in a chat room
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.role = :role " +
           "AND p.isActive = true " +
           "ORDER BY p.joinedAt ASC")
    List<ChatParticipant> findByRoleInChatRoom(@Param("chatRoomId") Long chatRoomId, @Param("role") ChatParticipant.ParticipantRole role);

    /**
     * Get paginated list of chat rooms for user
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.user.id = :userId " +
           "AND p.isActive = true " +
           "ORDER BY p.chatRoom.lastActivityAt DESC")
    Page<ChatParticipant> findActiveByUserIdPaginated(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find recent participants in chat room (last N participants to join)
     */
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.isActive = true " +
           "ORDER BY p.joinedAt DESC")
    Page<ChatParticipant> findRecentParticipants(@Param("chatRoomId") Long chatRoomId, Pageable pageable);
}
