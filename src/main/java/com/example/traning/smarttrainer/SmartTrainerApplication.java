package com.example.traning.smarttrainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // これでスケジューラーが有効になります
@ComponentScan(basePackages = { "com.example.traning" })
public class SmartTrainerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartTrainerApplication.class, args);
    }
}