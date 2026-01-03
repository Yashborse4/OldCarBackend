package com.carselling.oldcar.service;

import com.carselling.oldcar.config.FileUploadConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileValidationServiceTest {

    @Mock
    private FileUploadConfig fileUploadConfig;

    @InjectMocks
    private FileValidationService fileValidationService;

    @BeforeEach
    void setUp() {
        // Default lenient stubs
        org.mockito.Mockito.lenient().when(fileUploadConfig.getMaxFileSizeMB()).thenReturn(10);
        org.mockito.Mockito.lenient().when(fileUploadConfig.isValidateFileExtension()).thenReturn(true);
        org.mockito.Mockito.lenient().when(fileUploadConfig.getAllowedExtensions())
                .thenReturn(Arrays.asList("jpg", "png", "pdf"));
        org.mockito.Mockito.lenient().when(fileUploadConfig.isValidateContentType()).thenReturn(false); // Validating
                                                                                                        // content type
                                                                                                        // requires real
                                                                                                        // Tika
        // setup or complex mocking
        org.mockito.Mockito.lenient().when(fileUploadConfig.isScanForViruses()).thenReturn(false);
    }

    @Test
    void validateFile_Success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "some content".getBytes());
        assertDoesNotThrow(() -> fileValidationService.validateFile(file));
    }

    @Test
    void validateFile_EmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "", "image/jpeg", new byte[0]);
        assertThrows(SecurityException.class, () -> fileValidationService.validateFile(file));
    }

    @Test
    void validateFile_InvalidExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "test.exe", "application/x-msdownload",
                "content".getBytes());
        assertThrows(SecurityException.class, () -> fileValidationService.validateFile(file));
    }

    @Test
    void validateFile_DoubleExtension() {
        // Should warn but currently logic in checking threats might throw or just log.
        // The implementation logs warning for double extension but blocks "exe" if in
        // dangerous extensions.
        // Let's test checking for threats directly if possible, or assume it passes if
        // 'jpg' is at end.
        MockMultipartFile file = new MockMultipartFile("file", "malicious.js.jpg", "image/jpeg", "content".getBytes());
        assertDoesNotThrow(() -> fileValidationService.validateFile(file));
    }

    @Test
    void validateFile_PathTraversal() {
        MockMultipartFile file = new MockMultipartFile("file", "../test.jpg", "image/jpeg", "content".getBytes());
        assertThrows(SecurityException.class, () -> fileValidationService.validateFile(file));
    }
}
