package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

       Page<ChatMessage> findByChatRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

       Optional<ChatMessage> findFirstByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

       @Query("SELECT m FROM ChatMessage m " +
                     "WHERE m.chatRoom.id = :chatRoomId " +
                     "AND m.isDeleted = false " +
                     "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
                     "ORDER BY m.createdAt DESC")
       Page<ChatMessage> searchInChat(@Param("chatRoomId") Long chatRoomId,
                     @Param("query") String query,
                     Pageable pageable);

       @Query("SELECT m FROM ChatMessage m " +
                     "JOIN m.chatRoom.participants p " +
                     "WHERE p.user.id = :userId " +
                     "AND m.isDeleted = false " +
                     "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
                     "ORDER BY m.createdAt DESC")
       Page<ChatMessage> searchUserChats(@Param("userId") Long userId,
                     @Param("query") String query,
                     Pageable pageable);

       @Query("SELECT m.chatRoom.id, COUNT(m) FROM ChatMessage m " +
                     "WHERE m.chatRoom.id IN (" +
                     "   SELECT p.chatRoom.id FROM com.carselling.oldcar.model.ChatParticipant p " +
                     "   WHERE p.user.id = :userId" +
                     ") " +
                     "AND m.isDeleted = false " +
                     "GROUP BY m.chatRoom.id")
       List<Object[]> getUnreadCountByChat(@Param("userId") Long userId);

       Optional<ChatMessage> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);

       // Optimized: Batch fetch latest messages for a list of chat room IDs
       // Uses a correlated subquery to find the Max ID per room, then fetches the
       // message
       @Query("SELECT m FROM ChatMessage m " +
                     "WHERE m.id IN (" +
                     "   SELECT MAX(m2.id) FROM ChatMessage m2 " +
                     "   WHERE m2.chatRoom.id IN :chatRoomIds " +
                     "   GROUP BY m2.chatRoom.id" +
                     ")")
       List<ChatMessage> findLatestMessagesByChatRoomIds(@Param("chatRoomIds") List<Long> chatRoomIds);
}
