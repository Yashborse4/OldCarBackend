// synced
package com.carselling.oldcar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.createXmlMapper(false).build();
        
        // Ensure visibility is set to ANY to match previous configuration
        mapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        
        // Support for Spring Security classes is usually handled by Spring Boot's builder 
        // if SecurityJackson2Modules is on the classpath, but we'll be explicit if needed.
        // The builder already includes standard modules like JavaTimeModule by default.
        
        return mapper;
    }
}
