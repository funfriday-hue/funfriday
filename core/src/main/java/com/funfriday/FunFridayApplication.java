package com.funfriday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// This annotation tells Spring to scan for your Controllers, Services, and Configs
public class FunFridayApplication {

    public static void main(String[] args) {
        // This line launches the internal Tomcat server and starts the backend
        SpringApplication.run(FunFridayApplication.class, args);
        System.out.println("🚀 FunFriday Backend is running on http://localhost:8080");
    }
}