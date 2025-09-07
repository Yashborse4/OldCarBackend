package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity to track message read status by users
 */
@Entity
@Table(name = "message_read", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessageV2 message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoomV2 chatRoom;

    @CreationTimestamp
    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    @Column(name = "is_delivered", nullable = false)
    @Builder.Default
    private Boolean isDelivered = false;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // Helper methods
    public void markAsDelivered() {
        this.isDelivered = true;
        this.deliveredAt = LocalDateTime.now();
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isMessageFromUser(Long userId) {
        return message.getSender().getId().equals(userId);
    }
}
