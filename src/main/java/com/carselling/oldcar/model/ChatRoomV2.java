package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enhanced Chat Room Entity V2 - Support for private and group chats
 */
@Entity
@Table(name = "chat_rooms_v2")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomV2 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id")
    private Car car;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatParticipant> participants;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatMessageV2> messages;

    public enum ChatType {
        PRIVATE,        // 1-on-1 chat
        GROUP,          // Group chat
        CAR_INQUIRY,    // Car inquiry chat
        DEALER_ONLY     // Dealer-only group
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastActivityAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getParticipantCount() {
        return participants != null ? participants.size() : 0;
    }
}
