package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataSourceLogger {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    public void log() {
        String password = System.getenv("DB_PASSWORD");
        int poolSize = Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE", "10"));
        System.out.println(datasourceUrl + " user=" + username + " pool=" + poolSize + " pwd=" + password);
    }
}
