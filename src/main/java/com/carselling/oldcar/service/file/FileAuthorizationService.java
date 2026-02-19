package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.service.car.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileAuthorizationService {

    private final CarService carService;
    private final ChatAuthorizationService chatAuthorizationService;

    /**
     * Check if user is authorized to upload to a specific folder
     */
    public void checkFolderAuthorization(String folder, User currentUser) {
        // STRICT AUTHORIZATION
        if (folder.startsWith("users/")) {
            long pathUserId = extractIdFromPath(folder, "users/");
            if (pathUserId != -1 && !currentUser.getId().equals(pathUserId) && !isAdmin(currentUser)) {
                throw new SecurityException("You are not authorized to upload to this folder");
            }
        } else if (folder.startsWith("cars/")) {
            long carId = extractIdFromPath(folder, "cars/");
            if (carId != -1) {
                try {
                    CarResponse car = carService.getVehicleById(String.valueOf(carId));
                    boolean isOwner = car.getDealerId().equals(currentUser.getId().toString());
                    if (!isOwner && !isAdmin(currentUser)) {
                        throw new SecurityException("You are not authorized to upload to this car's folder");
                    }
                } catch (ResourceNotFoundException e) {
                    // Start orphan check logic or strict failing
                    throw new SecurityException("Car not found or access denied");
                }
            }
        } else if (folder.startsWith("chat/")) {
            long chatId = extractIdFromPath(folder, "chat/");
            if (chatId != -1) {
                try {
                    chatAuthorizationService.assertCanSendMessage(currentUser.getId(), chatId);
                } catch (Exception e) {
                    throw new SecurityException("You are not authorized to upload to this chat");
                }
            }
        }
    }

    /**
     * Check if user is authorized to delete a file
     */
    public void checkDeletionAuthorization(String fileUrl, User currentUser) {
        if (!isAdmin(currentUser)) {
            if (fileUrl.contains("/users/")) {
                long pathUserId = extractIdFromUrl(fileUrl, "/users/");
                if (pathUserId != -1 && !currentUser.getId().equals(pathUserId)) {
                    throw new SecurityException("You can only delete your own profile files");
                }
            } else if (fileUrl.contains("/cars/")) {
                long carId = extractIdFromUrl(fileUrl, "/cars/");
                if (carId != -1) {
                    try {
                        CarResponse car = carService.getVehicleById(String.valueOf(carId));
                        if (!car.getDealerId().equals(currentUser.getId().toString())) {
                            throw new SecurityException("You cannot delete images from cars you do not own");
                        }
                    } catch (ResourceNotFoundException e) {
                        throw new SecurityException("Associated vehicle not found or access denied");
                    }
                }
            } else if (fileUrl.contains("/chat/")) {
                 long chatId = extractIdFromUrl(fileUrl, "/chat/");
                 if (chatId != -1) {
                     // For deletion, verify user is in chat. 
                     // Ideally we should check if they sent the message, but fileUrl doesn't imply message ownership directly.
                     // However, this checkDeletionAuthorization is for the file itself.
                     try {
                        chatAuthorizationService.assertCanViewChat(currentUser.getId(), chatId);
                     } catch (Exception e) {
                        throw new SecurityException("You are not authorized to delete files from this chat");
                     }
                 }
            }
        }
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    public long extractIdFromPath(String path, String prefix) {
        try {
            // Safe extraction preventing index out of bounds
            // Assuming format: "prefix{id}/..."
            int startIndex = path.indexOf(prefix);
            if (startIndex == -1)
                return -1;

            startIndex += prefix.length();
            int endIndex = path.indexOf("/", startIndex);
            if (endIndex == -1)
                endIndex = path.length();

            String idStr = path.substring(startIndex, endIndex);
            return Long.parseLong(idStr);
        } catch (Exception e) {
            log.warn("Failed to extract ID from path: {}", path);
            return -1;
        }
    }

    public long extractIdFromUrl(String url, String pattern) {
        try {
            // Handle both full URLs and paths
            int startIndex = url.indexOf(pattern);
            if (startIndex == -1)
                return -1;

            startIndex += pattern.length();
            int endIndex = url.indexOf("/", startIndex);
            if (endIndex == -1) {
                // Check if it might end with the ID (e.g. .../users/123)
                endIndex = url.length();
            }

            String idStr = url.substring(startIndex, endIndex);
            return Long.parseLong(idStr);
        } catch (Exception e) {
            log.warn("Failed to extract ID from URL: {}", url);
            return -1;
        }
    }
}
