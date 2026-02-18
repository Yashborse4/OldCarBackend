package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.ChatRoom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

       // 1. Fetch IDs for pagination (Fast, no joins)
       @Query("SELECT DISTINCT r.id FROM ChatRoom r JOIN ChatParticipant p ON p.chatRoom.id = r.id " +
                     "WHERE p.user.id = :userId ORDER BY r.lastActivityAt DESC")
       Page<Long> findChatRoomIdsByParticipantUserId(@Param("userId") Long userId, Pageable pageable);

       // 2. Fetch details for specific IDs (Fast, single query with joins)
       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "participants", "participants.user",
                     "car", "car.images" })
       @Query("SELECT DISTINCT r FROM ChatRoom r WHERE r.id IN :ids ORDER BY r.lastActivityAt DESC")
       java.util.List<ChatRoom> findAllByIdIn(@Param("ids") java.util.List<Long> ids);

       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "participants", "participants.user",
                     "car", "car.images" })
       @Query("SELECT DISTINCT r FROM ChatRoom r JOIN ChatParticipant p ON p.chatRoom.id = r.id " +
                     "WHERE p.user.id = :userId AND r.type = :type")
       Page<ChatRoom> findByParticipantUserIdAndType(@Param("userId") Long userId,
                     @Param("type") ChatRoom.ChatType type,
                     Pageable pageable);

       @Query("SELECT r FROM ChatRoom r " +
                     "JOIN ChatParticipant p1 ON p1.chatRoom = r AND p1.user.id = :userId1 " +
                     "JOIN ChatParticipant p2 ON p2.chatRoom = r AND p2.user.id = :userId2 " +
                     "WHERE r.type = com.carselling.oldcar.model.ChatRoom$ChatType.PRIVATE")
       Optional<ChatRoom> findPrivateChatBetweenUsers(@Param("userId1") Long userId1,
                     @Param("userId2") Long userId2);

       @Query("SELECT r FROM ChatRoom r " +
                     "WHERE r.car.id = :carId " +
                     "AND EXISTS (SELECT 1 FROM ChatParticipant p WHERE p.chatRoom = r AND p.user.id = :buyerId) " +
                     "AND EXISTS (SELECT 1 FROM ChatParticipant p WHERE p.chatRoom = r AND p.user.id = :sellerId) " +
                     "AND r.type = com.carselling.oldcar.model.ChatRoom$ChatType.CAR_INQUIRY")
       Optional<ChatRoom> findCarInquiryChat(@Param("carId") Long carId,
                     @Param("buyerId") Long buyerId,
                     @Param("sellerId") Long sellerId);

       @Query("SELECT COUNT(r) FROM ChatRoom r " +
                     "WHERE r.type = com.carselling.oldcar.model.ChatRoom$ChatType.CAR_INQUIRY " +
                     "AND (r.car.owner.id = :sellerId OR r.car.coOwner.id = :sellerId)")
       long countCarInquiryChatsForSeller(@Param("sellerId") Long sellerId);

       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "participants", "participants.user",
                     "car", "car.images" })
       @Query("SELECT r FROM ChatRoom r " +
                     "WHERE (r.car.owner.id = :dealerId OR r.car.coOwner.id = :dealerId) " +
                     "AND r.type = com.carselling.oldcar.model.ChatRoom$ChatType.CAR_INQUIRY " +
                     "ORDER BY r.lastActivityAt DESC")
       Page<ChatRoom> findDealerInquiries(@Param("dealerId") Long dealerId, Pageable pageable);

       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "participants", "participants.user",
                     "car", "car.images" })
       @Query("SELECT r FROM ChatRoom r " +
                     "WHERE (r.car.owner.id = :dealerId OR r.car.coOwner.id = :dealerId) " +
                     "AND r.type = com.carselling.oldcar.model.ChatRoom$ChatType.CAR_INQUIRY " +
                     "AND r.status = :status " +
                     "ORDER BY r.lastActivityAt DESC")
       Page<ChatRoom> findDealerInquiriesByStatus(@Param("dealerId") Long dealerId,
                     @Param("status") ChatRoom.InquiryStatus status,
                     Pageable pageable);

       // Optimized query to fetch Chat Rooms with Participant Count and optionally
       // Unread Count if joined
       // Note: Fetching last message efficiently is complex in JPQL across multiple
       // rooms.
       // Strategy: Fetch Room + Participants, then Batch Fetch Last Messages in
       // Service?
       // OR: Use EntityGraph to eagerness loaded participants.
       // Let's use EntityGraph for participants first to solve N+1 on participants
       // loop.
       // Deprecated: Use findChatRoomIdsByParticipantUserId + findAllByIdIn
       // Keeping for signature compatibility if needed, but implementation should
       // change
       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "participants", "participants.user",
                     "car", "car.images" })
       @Query("SELECT DISTINCT r FROM ChatRoom r JOIN r.participants p WHERE p.user.id = :userId ORDER BY r.lastActivityAt DESC")
       Page<ChatRoom> findChatRoomsWithParticipants(@Param("userId") Long userId, Pageable pageable);

       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "participants", "participants.user",
                     "car", "car.images" })
       @Query("SELECT DISTINCT r FROM ChatRoom r " +
                     "JOIN r.participants p " +
                     "WHERE p.user.id = :userId " +
                     "AND (LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
                     "OR EXISTS (SELECT 1 FROM ChatParticipant p2 WHERE p2.chatRoom = r " +
                     "AND (LOWER(p2.user.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                     "OR LOWER(p2.user.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                     "OR LOWER(p2.user.username) LIKE LOWER(CONCAT('%', :query, '%'))))) " +
                     "ORDER BY r.lastActivityAt DESC")
       Page<ChatRoom> searchRooms(@Param("query") String query, @Param("userId") Long userId, Pageable pageable);

       @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "participants", "participants.user",
                     "car", "car.images" })
       @Query("SELECT DISTINCT r FROM ChatRoom r " +
                     "JOIN ChatParticipant p ON p.chatRoom.id = r.id " +
                     "WHERE p.user.id = :userId " +
                     "AND r.car.id = :carId " +
                     "ORDER BY r.lastActivityAt DESC")
       java.util.List<ChatRoom> findByCarIdAndUserId(@Param("carId") Long carId, @Param("userId") Long userId);
}
