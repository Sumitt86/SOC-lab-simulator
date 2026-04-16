package com.minisoc.lab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:4173", "http://localhost:3000")
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");

        // Allow monitoring agent from any VM IP to POST events
        registry.addMapping("/api/events")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
