package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.ChatInviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatInviteLinkRepository extends JpaRepository<ChatInviteLink, Long> {
    Optional<ChatInviteLink> findByToken(String token);

    Optional<ChatInviteLink> findByChatRoomId(Long chatRoomId);
}
