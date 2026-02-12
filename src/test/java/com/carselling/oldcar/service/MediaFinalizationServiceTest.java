package com.carselling.oldcar.service;

import com.carselling.oldcar.b2.B2Client;
import com.carselling.oldcar.b2.B2Properties;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.TemporaryFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.TemporaryFileRepository;
import com.carselling.oldcar.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaFinalizationServiceTest {

    @Mock
    private B2Client b2Client;
    @Mock
    private B2Properties properties;
    @Mock
    private TemporaryFileRepository temporaryFileRepository;
    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @InjectMocks
    private MediaFinalizationService mediaFinalizationService;

    private User user;
    private TemporaryFile imageFile;
    private TemporaryFile videoFile;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("dealer1");

        imageFile = new TemporaryFile();
        imageFile.setId(101L);
        imageFile.setFileName("image.jpg");
        imageFile.setOriginalFileName("image.jpg");
        imageFile.setContentType("image/jpeg");
        imageFile.setFileSize(1000L);
        imageFile.setUploadedBy(user);
        imageFile.setFileUrl("https://cdn.example.com/temp/image.jpg");
        imageFile.setFileId("temp-image-id");

        videoFile = new TemporaryFile();
        videoFile.setId(102L);
        videoFile.setFileName("video.mp4");
        videoFile.setOriginalFileName("video.mp4");
        videoFile.setContentType("video/mp4");
        videoFile.setFileSize(5000000L);
        videoFile.setUploadedBy(user);
        videoFile.setFileUrl("https://cdn.example.com/temp/video.mp4");
        videoFile.setFileId("temp-video-id");

        lenient().when(properties.getCdnDomain()).thenReturn("https://cdn.example.com");
        lenient().when(properties.getBucketId()).thenReturn("bucket-id");
    }

    @Test
    void finalizeUploads_segregatesImagesAndVideos() {
        // Arrange
        when(temporaryFileRepository.findById(101L)).thenReturn(Optional.of(imageFile));
        when(temporaryFileRepository.findById(102L)).thenReturn(Optional.of(videoFile));

        when(b2Client.copyFile(eq("temp-image-id"), anyString())).thenReturn("new-image-id");
        when(b2Client.copyFile(eq("temp-video-id"), anyString())).thenReturn("new-video-id");

        // Act
        mediaFinalizationService.finalizeUploads(List.of(101L, 102L), "cars/10", ResourceType.CAR_IMAGE, 10L, user);

        // Assert
        // Image should go to cars/10/images/image.jpg
        verify(b2Client).copyFile("temp-image-id", "cars/10/images/image.jpg");

        // Video should go to cars/10/videos/video.mp4
        verify(b2Client).copyFile("temp-video-id", "cars/10/videos/video.mp4");
    }
}
