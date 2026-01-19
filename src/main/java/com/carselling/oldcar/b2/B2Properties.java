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
    private String cdnDomain; // e.g. https://files.yoursite.com
}
