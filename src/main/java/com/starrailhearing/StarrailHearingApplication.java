package com.starrailhearing;

import com.starrailhearing.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class StarrailHearingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarrailHearingApplication.class, args);
    }
}
