package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarDocumentDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarDocument;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarDocumentRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarDocumentService {
    
    private final CarDocumentRepository carDocumentRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public CarDocumentDto uploadDocument(Long carId, MultipartFile file, String documentType, String description) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        try {
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            String filePath = "/uploads/car-documents/" + fileName;
            
            CarDocument document = CarDocument.builder()
                    .car(car)
                    .user(currentUser)
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .documentType(documentType)
                    .description(description)
                    .isVerified(false)
                    .build();
            
            carDocumentRepository.save(document);
            
            return convertToDto(document);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload document", e);
        }
    }
    
    public List<CarDocumentDto> getCarDocuments(Long carId) {
        List<CarDocument> documents = carDocumentRepository.findByCarId(carId);
        return documents.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public CarDocumentDto getDocument(Long documentId) {
        CarDocument document = carDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        return convertToDto(document);
    }
    
    public void deleteDocument(Long documentId) {
        carDocumentRepository.deleteById(documentId);
    }
    
    public CarDocumentDto verifyDocument(Long documentId) {
        CarDocument document = carDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        document.setVerified(true);
        document.setVerifiedAt(LocalDateTime.now());
        
        carDocumentRepository.save(document);
        
        return convertToDto(document);
    }
    
    private CarDocumentDto convertToDto(CarDocument document) {
        return CarDocumentDto.builder()
                .id(document.getId())
                .carId(document.getCar().getId())
                .userId(document.getUser().getId())
                .fileName(document.getFileName())
                .filePath(document.getFilePath())
                .fileSize(document.getFileSize())
                .mimeType(document.getMimeType())
                .documentType(document.getDocumentType())
                .description(document.getDescription())
                .isVerified(document.getIsVerified())
                .verifiedAt(document.getVerifiedAt())
                .createdAt(document.getCreatedAt())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
