package com.phoebe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PhoebeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhoebeServiceApplication.class, args);
    }
}
