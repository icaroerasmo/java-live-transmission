package com.icaroerasmo.livetransmission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JavaLiveTransmissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaLiveTransmissionApplication.class, args);
    }
}
