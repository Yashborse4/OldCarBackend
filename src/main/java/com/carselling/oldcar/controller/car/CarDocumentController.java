package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarDocumentDto;
import com.carselling.oldcar.service.car.CarDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/documents")
@RequiredArgsConstructor
public class CarDocumentController {
    
    private final CarDocumentService carDocumentService;
    
    @PostMapping("/upload/{carId}")
    public ResponseEntity<CarDocumentDto> uploadDocument(@PathVariable Long carId, 
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam("documentType") String documentType,
                                                        @RequestParam(value = "description", required = false) String description) {
        return ResponseEntity.ok(carDocumentService.uploadDocument(carId, file, documentType, description));
    }
    
    @GetMapping("/car/{carId}")
    public ResponseEntity<List<CarDocumentDto>> getCarDocuments(@PathVariable Long carId) {
        return ResponseEntity.ok(carDocumentService.getCarDocuments(carId));
    }
    
    @GetMapping("/{documentId}")
    public ResponseEntity<CarDocumentDto> getDocument(@PathVariable Long documentId) {
        return ResponseEntity.ok(carDocumentService.getDocument(documentId));
    }
    
    @DeleteMapping("/delete/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        carDocumentService.deleteDocument(documentId);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/verify/{documentId}")
    public ResponseEntity<CarDocumentDto> verifyDocument(@PathVariable Long documentId) {
        return ResponseEntity.ok(carDocumentService.verifyDocument(documentId));
    }
}
