package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupInviteDto {
    private Long id;
    private Long chatRoomId;
    private String chatRoomName;
    private Long inviterId;
    private String inviterName;
    private LocalDateTime createdAt;
}
