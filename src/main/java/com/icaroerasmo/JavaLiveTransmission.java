package com.icaroerasmo;

import com.icaroerasmo.properties.LiveTransmissionProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@Log4j2
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(LiveTransmissionProperties.class)
public class JavaLiveTransmission {

    public static void main(String[] args) {
        SpringApplication.run(JavaLiveTransmission.class, args);
    }
}
