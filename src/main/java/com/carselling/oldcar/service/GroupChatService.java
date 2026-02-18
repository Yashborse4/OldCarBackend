package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.chat.ChatRoomDto;
import com.carselling.oldcar.dto.chat.UpdateGroupDetailsRequest;
import org.springframework.web.multipart.MultipartFile;

public interface GroupChatService {

    /**
     * Update group chat name and description
     */
    ChatRoomDto updateGroupDetails(Long chatId, UpdateGroupDetailsRequest request, Long requesterId);

    /**
     * Update group chat profile image
     */
    ChatRoomDto updateGroupImage(Long chatId, MultipartFile file, Long requesterId);
}
