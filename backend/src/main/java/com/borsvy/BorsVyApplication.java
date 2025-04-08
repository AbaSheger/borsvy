package com.borsvy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@EntityScan("com.borsvy.model")
@EnableJpaRepositories("com.borsvy.repository")
@EnableRetry
public class BorsVyApplication {
    public static void main(String[] args) {
        System.setProperty("server.address", "0.0.0.0");
        System.setProperty("server.port", "8080");
        SpringApplication.run(BorsVyApplication.class, args);
    }
}