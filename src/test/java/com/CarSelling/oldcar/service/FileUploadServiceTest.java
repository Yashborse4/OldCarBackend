package com.carselling.oldcar.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.carselling.oldcar.config.S3Config;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileUploadService
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock
    private AmazonS3 amazonS3Client;

    @Mock
    private S3Config s3Config;

    @Mock
    private MultipartFile mockFile;

    @InjectMocks
    private FileUploadService fileUploadService;

    private final String bucketName = "test-bucket";
    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        when(s3Config.getBucketName()).thenReturn(bucketName);
    }

    @Test
    void testUploadFile_ValidImageFile_Success() throws IOException {
        // Arrange
        String originalFilename = "test-image.jpg";
        String contentType = "image/jpeg";
        long fileSize = 1024 * 1024; // 1MB
        byte[] fileContent = "test image content".getBytes();

        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn(originalFilename);
        when(mockFile.getContentType()).thenReturn(contentType);
        when(mockFile.getSize()).thenReturn(fileSize);
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(fileContent));

        URL mockUrl = mock(URL.class);
        when(mockUrl.toString()).thenReturn("https://test-bucket.s3.amazonaws.com/folder/test-file.jpg");
        when(amazonS3Client.putObject(any(PutObjectRequest.class))).thenReturn(null);
        when(amazonS3Client.getUrl(eq(bucketName), anyString())).thenReturn(mockUrl);

        // Act
        FileUploadResponse result = fileUploadService.uploadFile(mockFile, "images", userId);

        // Assert
        assertNotNull(result);
        assertEquals(originalFilename, result.getOriginalFileName());
        assertEquals(contentType, result.getContentType());
        assertEquals(fileSize, result.getFileSize());
        assertEquals("images", result.getFolder());
        assertTrue(result.getFileUrl().contains("test-bucket"));

        verify(amazonS3Client).putObject(any(PutObjectRequest.class));
    }

    @Test
    void testUploadFile_EmptyFile_ThrowsException() {
        // Arrange
        when(mockFile.isEmpty()).thenReturn(true);

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> {
            fileUploadService.uploadFile(mockFile, "folder", userId);
        });

        assertEquals("File is empty", exception.getMessage());
        verify(amazonS3Client, never()).putObject(any(PutObjectRequest.class));
    }

    @Test
    void testUploadFile_UnsupportedFileType_ThrowsException() {
        // Arrange
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getContentType()).thenReturn("application/x-executable");
        when(mockFile.getSize()).thenReturn(1024L);

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> {
            fileUploadService.uploadFile(mockFile, "folder", userId);
        });

        assertTrue(exception.getMessage().contains("File type not allowed"));
        verify(amazonS3Client, never()).putObject(any(PutObjectRequest.class));
    }

    @Test
    void testUploadFile_FileTooLarge_ThrowsException() {
        // Arrange
        long maxImageSize = 10 * 1024 * 1024; // 10MB
        long fileSize = maxImageSize + 1; // Exceed limit

        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getSize()).thenReturn(fileSize);

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> {
            fileUploadService.uploadFile(mockFile, "folder", userId);
        });

        assertTrue(exception.getMessage().contains("File size exceeds limit"));
        verify(amazonS3Client, never()).putObject(any(PutObjectRequest.class));
    }

    @Test
    void testUploadFile_InvalidImageExtension_ThrowsException() {
        // Arrange
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn("test.exe");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getSize()).thenReturn(1024L);

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> {
            fileUploadService.uploadFile(mockFile, "folder", userId);
        });

        assertTrue(exception.getMessage().contains("Invalid image file extension"));
        verify(amazonS3Client, never()).putObject(any(PutObjectRequest.class));
    }

    @Test
    void testDeleteFile_Success() {
        // Arrange
        String fileUrl = "https://test-bucket.s3.amazonaws.com/folder/test-file.jpg";
        doNothing().when(amazonS3Client).deleteObject(bucketName, "folder/test-file.jpg");

        // Act
        boolean result = fileUploadService.deleteFile(fileUrl);

        // Assert
        assertTrue(result);
        verify(amazonS3Client).deleteObject(bucketName, "folder/test-file.jpg");
    }

    @Test
    void testDeleteFile_S3Exception_ReturnsFalse() {
        // Arrange
        String fileUrl = "https://test-bucket.s3.amazonaws.com/folder/test-file.jpg";
        doThrow(new RuntimeException("S3 error")).when(amazonS3Client)
                .deleteObject(bucketName, "folder/test-file.jpg");

        // Act
        boolean result = fileUploadService.deleteFile(fileUrl);

        // Assert
        assertFalse(result);
        verify(amazonS3Client).deleteObject(bucketName, "folder/test-file.jpg");
    }

    @Test
    void testGeneratePresignedUrl_Success() {
        // Arrange
        String fileUrl = "https://test-bucket.s3.amazonaws.com/folder/test-file.jpg";
        int expirationMinutes = 60;
        
        URL presignedUrl = mock(URL.class);
        when(presignedUrl.toString()).thenReturn("https://presigned-url.com");
        when(amazonS3Client.generatePresignedUrl(any())).thenReturn(presignedUrl);

        // Act
        String result = fileUploadService.generatePresignedUrl(fileUrl, expirationMinutes);

        // Assert
        assertNotNull(result);
        assertEquals("https://presigned-url.com", result);
        verify(amazonS3Client).generatePresignedUrl(any());
    }

    @Test
    void testGeneratePresignedUrl_Exception_ReturnsOriginalUrl() {
        // Arrange
        String fileUrl = "https://test-bucket.s3.amazonaws.com/folder/test-file.jpg";
        int expirationMinutes = 60;
        
        when(amazonS3Client.generatePresignedUrl(any()))
                .thenThrow(new RuntimeException("S3 error"));

        // Act
        String result = fileUploadService.generatePresignedUrl(fileUrl, expirationMinutes);

        // Assert
        assertEquals(fileUrl, result); // Should return original URL as fallback
    }

    @Test
    void testGetFileMetadata_Success() {
        // Arrange
        String fileUrl = "https://test-bucket.s3.amazonaws.com/folder/test-file.jpg";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("image/jpeg");
        metadata.setContentLength(1024);

        when(amazonS3Client.getObjectMetadata(bucketName, "folder/test-file.jpg"))
                .thenReturn(metadata);

        // Act
        ObjectMetadata result = fileUploadService.getFileMetadata(fileUrl);

        // Assert
        assertNotNull(result);
        assertEquals("image/jpeg", result.getContentType());
        assertEquals(1024, result.getContentLength());
    }

    @Test
    void testGetFileMetadata_Exception_ReturnsNull() {
        // Arrange
        String fileUrl = "https://test-bucket.s3.amazonaws.com/folder/test-file.jpg";
        when(amazonS3Client.getObjectMetadata(bucketName, "folder/test-file.jpg"))
                .thenThrow(new RuntimeException("S3 error"));

        // Act
        ObjectMetadata result = fileUploadService.getFileMetadata(fileUrl);

        // Assert
        assertNull(result);
    }

    @Test
    void testExtractKeyFromUrl_StandardFormat() throws Exception {
        // This tests the private method indirectly through deleteFile
        String fileUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/folder/subfolder/test-file.jpg";
        
        doNothing().when(amazonS3Client).deleteObject(bucketName, "folder/subfolder/test-file.jpg");

        // Act
        boolean result = fileUploadService.deleteFile(fileUrl);

        // Assert
        assertTrue(result);
        verify(amazonS3Client).deleteObject(bucketName, "folder/subfolder/test-file.jpg");
    }

    @Test
    void testUploadFile_PDFDocument_Success() throws IOException {
        // Arrange
        String originalFilename = "document.pdf";
        String contentType = "application/pdf";
        long fileSize = 5 * 1024 * 1024; // 5MB
        byte[] fileContent = "test pdf content".getBytes();

        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn(originalFilename);
        when(mockFile.getContentType()).thenReturn(contentType);
        when(mockFile.getSize()).thenReturn(fileSize);
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(fileContent));

        URL mockUrl = mock(URL.class);
        when(mockUrl.toString()).thenReturn("https://test-bucket.s3.amazonaws.com/documents/document.pdf");
        when(amazonS3Client.putObject(any(PutObjectRequest.class))).thenReturn(null);
        when(amazonS3Client.getUrl(eq(bucketName), anyString())).thenReturn(mockUrl);

        // Act
        FileUploadResponse result = fileUploadService.uploadFile(mockFile, "documents", userId);

        // Assert
        assertNotNull(result);
        assertEquals(originalFilename, result.getOriginalFileName());
        assertEquals(contentType, result.getContentType());
        assertTrue(result.isDocumentFile());
        assertFalse(result.isImageFile());
        
        verify(amazonS3Client).putObject(any(PutObjectRequest.class));
    }
}
