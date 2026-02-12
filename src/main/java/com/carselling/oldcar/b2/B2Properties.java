package com.carselling.oldcar.b2;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "b2")
public class B2Properties {
    private String applicationKeyId;
    private String applicationKey;
    private String bucketId;
    private String bucketName; // Added for fallback download by name
    private String cdnDomain; // e.g. https://files.yoursite.com

    public String getApplicationKeyId() {
        return applicationKeyId;
    }

    public void setApplicationKeyId(String applicationKeyId) {
        this.applicationKeyId = applicationKeyId;
    }

    public String getApplicationKey() {
        return applicationKey;
    }

    public void setApplicationKey(String applicationKey) {
        this.applicationKey = applicationKey;
    }

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }

    public String getCdnDomain() {
        return cdnDomain;
    }

    public void setCdnDomain(String cdnDomain) {
        this.cdnDomain = cdnDomain;
    }
}
