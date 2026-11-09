package com.queryai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QueryAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueryAiApplication.class, args);
    }
}
