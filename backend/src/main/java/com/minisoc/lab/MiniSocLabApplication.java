package com.minisoc.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiniSocLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniSocLabApplication.class, args);
    }
}
