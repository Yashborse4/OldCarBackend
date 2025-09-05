package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.ChatMessageV2;
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
 * Repository for ChatMessageV2 entity with enhanced query methods
 */
@Repository
public interface ChatMessageV2Repository extends JpaRepository<ChatMessageV2, Long> {

    /**
     * Find messages in a chat room ordered by timestamp (newest first)
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessageV2> findByChatRoomIdAndIsDeletedFalse(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    /**
     * Find messages in a chat room after certain timestamp
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.createdAt > :timestamp " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessageV2> findByChatRoomIdAfterTimestamp(@Param("chatRoomId") Long chatRoomId, @Param("timestamp") LocalDateTime timestamp);

    /**
     * Find unread messages for a user in a specific chat room
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false " +
           "AND m.id NOT IN (SELECT mr.message.id FROM MessageRead mr WHERE mr.user.id = :userId) " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessageV2> findUnreadMessagesForUser(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

    /**
     * Count unread messages for a user in a specific chat room
     */
    @Query("SELECT COUNT(m) FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false " +
           "AND m.id NOT IN (SELECT mr.message.id FROM MessageRead mr WHERE mr.user.id = :userId)")
    long countUnreadMessagesForUser(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

    /**
     * Count total unread messages for a user across all chat rooms
     */
    @Query("SELECT COUNT(m) FROM ChatMessageV2 m " +
           "JOIN m.chatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE p.user.id = :userId AND p.isActive = true " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false " +
           "AND m.id NOT IN (SELECT mr.message.id FROM MessageRead mr WHERE mr.user.id = :userId)")
    long countTotalUnreadMessagesForUser(@Param("userId") Long userId);

    /**
     * Search messages in a chat room by content
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.isDeleted = false " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessageV2> searchMessagesInChatRoom(@Param("chatRoomId") Long chatRoomId, @Param("query") String query, Pageable pageable);

    /**
     * Search messages across all user's chat rooms
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "JOIN m.chatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE p.user.id = :userId AND p.isActive = true " +
           "AND m.isDeleted = false " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessageV2> searchMessagesForUser(@Param("userId") Long userId, @Param("query") String query, Pageable pageable);

    /**
     * Find latest message in a chat room
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Optional<ChatMessageV2> findLatestMessageInChatRoom(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find messages by sender in a chat room
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.sender.id = :senderId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessageV2> findBySenderInChatRoom(@Param("chatRoomId") Long chatRoomId, @Param("senderId") Long senderId, Pageable pageable);

    /**
     * Find messages with attachments in a chat room
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.attachmentUrl IS NOT NULL " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessageV2> findMessagesWithAttachments(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    /**
     * Soft delete message
     */
    @Modifying
    @Query("UPDATE ChatMessageV2 m SET m.isDeleted = true, m.updatedAt = :updatedAt " +
           "WHERE m.id = :messageId AND m.sender.id = :senderId")
    int softDeleteMessage(@Param("messageId") Long messageId, @Param("senderId") Long senderId, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Find reply messages to a specific message
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.replyTo.id = :parentMessageId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessageV2> findRepliesByParentMessageId(@Param("parentMessageId") Long parentMessageId);

    /**
     * Count messages in a chat room
     */
    @Query("SELECT COUNT(m) FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.isDeleted = false")
    long countMessagesByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find messages by type in a chat room
     */
    @Query("SELECT m FROM ChatMessageV2 m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.messageType = :messageType " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessageV2> findByMessageTypeInChatRoom(@Param("chatRoomId") Long chatRoomId, @Param("messageType") ChatMessageV2.MessageType messageType, Pageable pageable);
}
