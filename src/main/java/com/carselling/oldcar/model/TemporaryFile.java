package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "temporary_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemporaryFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fileUrl;

    @Column(nullable = false, unique = true)
    private String fileId;

    @Column(nullable = false)
    private String fileName;

    private String originalFileName;

    @Column(name = "file_hash", length = 64)
    private String fileHash; // SHA-1 or SHA-256

    private Long fileSize;

    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
