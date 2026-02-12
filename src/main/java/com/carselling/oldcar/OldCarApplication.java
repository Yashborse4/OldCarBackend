package com.carselling.oldcar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
@EnableRetry
public class OldCarApplication {

    public static void main(String[] args) {
        SpringApplication.run(OldCarApplication.class, args);
    }

}
