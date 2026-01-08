package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for MessageRead entity to track message read status
 */
@Repository
public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {

    /**
     * Check if a message has been read by a specific user
     */
    @Query("SELECT CASE WHEN COUNT(mr) > 0 THEN true ELSE false END " +
           "FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId " +
           "AND mr.user.id = :userId")
    boolean isMessageReadByUser(@Param("messageId") Long messageId, @Param("userId") Long userId);

    /**
     * Find message read record by message and user
     */
    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId " +
           "AND mr.user.id = :userId")
    Optional<MessageRead> findByMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);

    /**
     * Find all read messages for a user in a specific chat room
     */
    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.user.id = :userId " +
           "AND mr.message.chatRoom.id = :chatRoomId " +
           "ORDER BY mr.readAt DESC")
    List<MessageRead> findByUserIdAndChatRoomId(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId);

    /**
     * Find users who have read a specific message
     */
    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId " +
           "ORDER BY mr.readAt ASC")
    List<MessageRead> findByMessageId(@Param("messageId") Long messageId);

    /**
     * Count how many users have read a specific message
     */
    @Query("SELECT COUNT(mr) FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId")
    long countReadsByMessageId(@Param("messageId") Long messageId);

    /**
     * Find latest read timestamp for user in a chat room
     */
    @Query("SELECT MAX(mr.readAt) FROM MessageRead mr " +
           "WHERE mr.user.id = :userId " +
           "AND mr.message.chatRoom.id = :chatRoomId")
    Optional<LocalDateTime> findLatestReadTimeByUserAndChatRoom(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId);

    /**
     * Mark multiple messages as read for a user (bulk operation)
     */
    @Modifying
    @Query("INSERT INTO MessageRead (message, user, readAt) " +
           "SELECT m, u, :readAt FROM ChatMessage m, User u " +
           "WHERE m.id IN :messageIds " +
           "AND u.id = :userId " +
           "AND NOT EXISTS (SELECT 1 FROM MessageRead mr WHERE mr.message.id = m.id AND mr.user.id = u.id)")
    int markMessagesAsRead(@Param("messageIds") List<Long> messageIds, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    /**
     * Delete read records for a specific message (when message is deleted)
     */
    @Modifying
    @Query("DELETE FROM MessageRead mr WHERE mr.message.id = :messageId")
    int deleteByMessageId(@Param("messageId") Long messageId);

    /**
     * Delete read records for a specific user (when user account is deleted)
     */
    @Modifying
    @Query("DELETE FROM MessageRead mr WHERE mr.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * Find messages read by user after a specific timestamp
     */
    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.user.id = :userId " +
           "AND mr.readAt > :timestamp " +
           "ORDER BY mr.readAt DESC")
    List<MessageRead> findByUserIdAfterTimestamp(@Param("userId") Long userId, @Param("timestamp") LocalDateTime timestamp);

    /**
     * Get read status summary for messages in a chat room
     */
    @Query("SELECT mr.message.id, COUNT(mr) FROM MessageRead mr " +
           "WHERE mr.message.chatRoom.id = :chatRoomId " +
           "GROUP BY mr.message.id " +
           "ORDER BY mr.message.createdAt DESC")
    List<Object[]> getReadCountsByMessageInChatRoom(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find unread messages for user in chat room (messages that exist but no read record)
     */
    @Query("SELECT m.id FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false " +
           "AND NOT EXISTS (SELECT 1 FROM MessageRead mr WHERE mr.message.id = m.id AND mr.user.id = :userId) " +
           "ORDER BY m.createdAt ASC")
    List<Long> findUnreadMessageIds(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

    /**
     * Mark all messages in chat room as read for user up to a specific message
     */
    @Modifying
    @Query("INSERT INTO MessageRead (message, user, readAt) " +
           "SELECT m, u, :readAt FROM ChatMessage m, User u " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.id <= :lastMessageId " +
           "AND m.sender.id != :userId " +
           "AND u.id = :userId " +
           "AND NOT EXISTS (SELECT 1 FROM MessageRead mr WHERE mr.message.id = m.id AND mr.user.id = u.id)")
    int markAllMessagesAsReadUpTo(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId, 
                                  @Param("lastMessageId") Long lastMessageId, @Param("readAt") LocalDateTime readAt);

    /**
     * Get read receipts for a specific message (who read it and when)
     */
    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId " +
           "ORDER BY mr.readAt ASC")
    List<MessageRead> getReadReceiptsForMessage(@Param("messageId") Long messageId);
}
