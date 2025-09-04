package com.carselling.oldcar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OldCarApplication {

    public static void main(String[] args) {
        SpringApplication.run(OldCarApplication.class, args);
    }

}
