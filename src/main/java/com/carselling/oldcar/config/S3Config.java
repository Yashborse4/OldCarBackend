package com.carselling.oldcar.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AWS S3 Configuration for file upload functionality
 */
@Configuration
public class S3Config {

    @Value("${aws.access-key:}")
    private String awsAccessKey;

    @Value("${aws.secret-key:}")
    private String awsSecretKey;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.s3.bucket-name:car-selling-app-files}")
    private String bucketName;

    /**
     * Create AWS S3 client bean
     */
    @Bean
    public AmazonS3 amazonS3Client() {
        // Use environment variables or IAM roles if access keys are not provided
        if (awsAccessKey.isEmpty() || awsSecretKey.isEmpty()) {
            // This will use default credential provider chain (IAM roles, environment variables, etc.)
            return AmazonS3ClientBuilder.standard()
                    .withRegion(Regions.fromName(awsRegion))
                    .build();
        }

        AWSCredentials credentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey);
        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.fromName(awsRegion))
                .build();
    }

    /**
     * Get S3 bucket name
     */
    public String getBucketName() {
        return bucketName;
    }
}
