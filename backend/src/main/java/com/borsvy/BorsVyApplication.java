package com.borsvy;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@EntityScan("com.borsvy.model")
@EnableJpaRepositories("com.borsvy.repository")
@EnableRetry
public class BorsVyApplication {
    public static void main(String[] args) {
        // Load environment variables from .env file if it exists, otherwise ignore.
        // Environment variables set in Railway will still be loaded automatically by Spring Boot.
        Dotenv dotenv = Dotenv.configure()
                             .ignoreIfMissing()
                             .load();
        // Setting system properties from dotenv might be redundant in production
        // as Spring Boot picks up platform env vars, but keep for local .env consistency.
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        
        SpringApplication.run(BorsVyApplication.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}