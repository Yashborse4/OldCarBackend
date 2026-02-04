package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.ChatGroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatGroupInviteRepository extends JpaRepository<ChatGroupInvite, Long> {

    List<ChatGroupInvite> findByInviteeId(Long inviteeId);

    Optional<ChatGroupInvite> findByChatRoomIdAndInviteeId(Long chatRoomId, Long inviteeId);

    boolean existsByChatRoomIdAndInviteeId(Long chatRoomId, Long inviteeId);
}
