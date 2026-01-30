package com.example.envdoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EnvDocAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnvDocAgentApplication.class, args);
    }
}
