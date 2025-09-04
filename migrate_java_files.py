#!/usr/bin/env python3
"""
Script to migrate Java files from old package structure to new one
and update all package declarations and imports.
"""

import os
import re
import shutil
from pathlib import Path

# Define paths
BASE_DIR = Path(r"D:\Startup\Car Frontend Backend\Sell-the-old-Car")
OLD_SRC = BASE_DIR / "src" / "main" / "java" / "com" / "CarSelling" / "Sell" / "the" / "old" / "Car"
NEW_SRC = BASE_DIR / "src" / "main" / "java" / "com" / "carselling" / "oldcar"
OLD_TEST = BASE_DIR / "src" / "test" / "java" / "com" / "CarSelling" / "Sell" / "the" / "old" / "Car"
NEW_TEST = BASE_DIR / "src" / "test" / "java" / "com" / "carselling" / "oldcar"

# Old and new package names
OLD_PACKAGE = "com.CarSelling.Sell.the.old.Car"
NEW_PACKAGE = "com.carselling.oldcar"

# File mapping rules - where each file should go in the new structure
FILE_MAPPINGS = {
    # Main application
    "SellTheOldCarApplication.java": "OldCarApplication.java",
    
    # Config files
    "config/CorsConfig.java": "config/CorsConfig.java",
    "config/ScheduledTasks.java": "config/ScheduledTasks.java",
    "config/WebSocketConfig.java": "config/WebSocketConfig.java",
    "config/WebSocketEventListener.java": "config/WebSocketEventListener.java",
    
    # Controllers - Auth
    "controller/AuthController.java": "controller/auth/AuthController.java",
    
    # Controllers - Car
    "controller/Car/CarController.java": "controller/car/CarController.java",
    "controller/Car/SecureCarController.java": "controller/car/SecureCarController.java",
    "controller/CarManagementController.java": "controller/car/CarManagementController.java",
    
    # Controllers - User
    "controller/User/UserController.java": "controller/user/UserController.java",
    
    # Controllers - Dealer
    "controller/SellerController.java": "controller/dealer/SellerController.java",
    
    # Controllers - Chat
    "controller/chat/ChatController.java": "controller/chat/ChatController.java",
    "controller/chat/ChatRestController.java": "controller/chat/ChatRestController.java",
    "controller/chat/ChatWebSocketController.java": "controller/chat/ChatWebSocketController.java",
    
    # DTOs - Auth
    "security/AuthPayload.java": "dto/auth/AuthPayload.java",
    "security/jwt/JwtAuthResponse.java": "dto/auth/JwtAuthResponse.java",
    "dto/UserDTO/LoginRequest.java": "dto/auth/LoginRequest.java",
    "dto/UserDTO/LoginInput.java": "dto/auth/LoginInput.java",
    "dto/UserDTO/RegisterRequest.java": "dto/auth/RegisterRequest.java",
    "dto/UserDTO/ForgotPasswordRequest.java": "dto/auth/ForgotPasswordRequest.java",
    "dto/UserDTO/ResetPasswordRequest.java": "dto/auth/ResetPasswordRequest.java",
    "dto/UserDTO/RefreshTokenRequest.java": "dto/auth/RefreshTokenRequest.java",
    
    # DTOs - User
    "dto/UserDTO/UpdateUserInput.java": "dto/user/UpdateUserInput.java",
    "dto/UserPage.java": "dto/user/UserPage.java",
    
    # DTOs - Car
    "dto/CarDTO/CarInput.java": "dto/car/CarInput.java",
    "dto/CarDTO/UpdateCarInput.java": "dto/car/UpdateCarInput.java",
    "dto/CarDTO/CarResponseDTO.java": "dto/car/CarResponseDTO.java",
    
    # Entities (models -> entity)
    "model/User.java": "entity/User.java",
    "model/Car.java": "entity/Car.java",
    "model/Role.java": "entity/Role.java",
    "model/Permission.java": "entity/Permission.java",
    "model/SellerType.java": "entity/SellerType.java",
    "model/OtpToken.java": "entity/OtpToken.java",
    
    # Chat Entities
    "model/Chat.java": "entity/chat/Chat.java",
    "model/ChatParticipant.java": "entity/chat/ChatParticipant.java",
    "model/Message.java": "entity/chat/Message.java",
    "model/MessageStatus.java": "entity/chat/MessageStatus.java",
    "model/UserStatus.java": "entity/chat/UserStatus.java",
    "model/chat/Chat.java": "entity/chat/Chat.java",
    "model/chat/ChatParticipant.java": "entity/chat/ChatParticipant.java",
    "model/chat/Message.java": "entity/chat/Message.java",
    "model/chat/MessageStatus.java": "entity/chat/MessageStatus.java",
    "model/chat/UserStatus.java": "entity/chat/UserStatus.java",
    
    # Exceptions
    "exception/GlobalExceptionHandler.java": "exception/GlobalExceptionHandler.java",
    "exception/ResourceNotFoundException.java": "exception/ResourceNotFoundException.java",
    "exception/ResourceAlreadyExistsException.java": "exception/ResourceAlreadyExistsException.java",
    "exception/UnauthorizedActionException.java": "exception/UnauthorizedActionException.java",
    
    # Repositories
    "repository/UserRepository.java": "repository/UserRepository.java",
    "repository/CarRepository.java": "repository/CarRepository.java",
    "repository/OtpTokenRepository.java": "repository/OtpTokenRepository.java",
    "repository/ChatRepository.java": "repository/ChatRepository.java",
    "repository/ChatParticipantRepository.java": "repository/ChatParticipantRepository.java",
    "repository/MessageRepository.java": "repository/MessageRepository.java",
    "repository/MessageStatusRepository.java": "repository/MessageStatusRepository.java",
    "repository/UserStatusRepository.java": "repository/UserStatusRepository.java",
    
    # Chat Repositories
    "repository/chat/ChatRepository.java": "repository/chat/ChatRepository.java",
    "repository/chat/ChatParticipantRepository.java": "repository/chat/ChatParticipantRepository.java",
    "repository/chat/MessageRepository.java": "repository/chat/MessageRepository.java",
    "repository/chat/MessageStatusRepository.java": "repository/chat/MessageStatusRepository.java",
    "repository/chat/UserStatusRepository.java": "repository/chat/UserStatusRepository.java",
    
    # Security
    "security/SecurityConfig.java": "security/SecurityConfig.java",
    "security/CustomUserDetailsService.java": "security/CustomUserDetailsService.java",
    "security/UserPrincipal.java": "security/UserPrincipal.java",
    "security/jwt/JwtAuthenticationEntryPoint.java": "security/jwt/JwtAuthenticationEntryPoint.java",
    "security/jwt/JwtAuthenticationFilter.java": "security/jwt/JwtAuthenticationFilter.java",
    "security/jwt/JwtTokenProvider.java": "security/jwt/JwtTokenProvider.java",
    
    # Services
    "service/AuthService.java": "service/AuthService.java",
    "service/AuthenticationService.java": "service/AuthenticationService.java",
    "service/UserService.java": "service/UserService.java",
    "service/CarService.java": "service/CarService.java",
    "service/OtpService.java": "service/OtpService.java",
    
    # Service Implementations
    "service/CarServiceImpl.java": "service/impl/CarServiceImpl.java",
    
    # Chat Services
    "service/chat/ChatService.java": "service/chat/ChatService.java",
    "service/chat/ChatSecurityService.java": "service/chat/ChatSecurityService.java",
    "service/chat/MessageService.java": "service/chat/MessageService.java",
    "service/chat/MessageDeliveryService.java": "service/chat/MessageDeliveryService.java",
    "service/chat/RateLimitingService.java": "service/chat/RateLimitingService.java",
    "service/chat/UserStatusService.java": "service/chat/UserStatusService.java",
}

def update_package_and_imports(content, file_path):
    """Update package declaration and imports in Java file content."""
    
    # Update package declaration
    content = re.sub(
        r'package\s+com\.CarSelling\.Sell\.the\.old\.Car(\.[a-zA-Z0-9_.]+)?;',
        lambda m: f'package {NEW_PACKAGE}{m.group(1) if m.group(1) else ""};',
        content
    )
    
    # Update imports
    content = re.sub(
        r'import\s+com\.CarSelling\.Sell\.the\.old\.Car',
        f'import {NEW_PACKAGE}',
        content
    )
    
    # Update static imports
    content = re.sub(
        r'import\s+static\s+com\.CarSelling\.Sell\.the\.old\.Car',
        f'import static {NEW_PACKAGE}',
        content
    )
    
    # Fix specific package mappings based on new structure
    replacements = {
        f'{NEW_PACKAGE}.model.': f'{NEW_PACKAGE}.entity.',
        f'{NEW_PACKAGE}.dto.UserDTO.': f'{NEW_PACKAGE}.dto.auth.',
        f'{NEW_PACKAGE}.dto.CarDTO.': f'{NEW_PACKAGE}.dto.car.',
        f'{NEW_PACKAGE}.security.AuthPayload': f'{NEW_PACKAGE}.dto.auth.AuthPayload',
        f'{NEW_PACKAGE}.security.jwt.JwtAuthResponse': f'{NEW_PACKAGE}.dto.auth.JwtAuthResponse',
    }
    
    for old, new in replacements.items():
        content = content.replace(old, new)
    
    # Update class name if it's the main application
    if 'SellTheOldCarApplication' in content:
        content = content.replace('SellTheOldCarApplication', 'OldCarApplication')
    
    return content

def migrate_file(old_path, new_path):
    """Migrate a single Java file to new location with updated content."""
    try:
        # Read the original file
        with open(old_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Update package and imports
        content = update_package_and_imports(content, new_path)
        
        # Create parent directory if needed
        new_path.parent.mkdir(parents=True, exist_ok=True)
        
        # Write the updated file
        with open(new_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"✓ Migrated: {old_path.name} -> {new_path}")
        return True
    except Exception as e:
        print(f"✗ Failed to migrate {old_path.name}: {str(e)}")
        return False

def main():
    """Main migration process."""
    print("=" * 60)
    print("Starting Java code migration...")
    print("=" * 60)
    
    success_count = 0
    fail_count = 0
    
    # Process each file mapping
    for old_rel_path, new_rel_path in FILE_MAPPINGS.items():
        old_file = OLD_SRC / old_rel_path
        new_file = NEW_SRC / new_rel_path
        
        if old_file.exists():
            if migrate_file(old_file, new_file):
                success_count += 1
            else:
                fail_count += 1
        else:
            # Try to find the file in the old structure
            print(f"⚠ File not found: {old_rel_path}")
    
    # Migrate test files
    test_file = OLD_TEST / "SellTheOldCarApplicationTests.java"
    if test_file.exists():
        new_test_file = NEW_TEST / "OldCarApplicationTests.java"
        if migrate_file(test_file, new_test_file):
            success_count += 1
        else:
            fail_count += 1
    
    print("\n" + "=" * 60)
    print(f"Migration completed!")
    print(f"✓ Successfully migrated: {success_count} files")
    if fail_count > 0:
        print(f"✗ Failed: {fail_count} files")
    print("=" * 60)
    
    # Additional instructions
    print("\n📝 Next steps:")
    print("1. Update build.gradle:")
    print("   - Change group from 'com.CarSelling' to 'com.carselling'")
    print("2. Update application.properties if needed")
    print("3. Run: ./gradlew clean build")
    print("4. Delete the old package structure after verification")

if __name__ == "__main__":
    main()
