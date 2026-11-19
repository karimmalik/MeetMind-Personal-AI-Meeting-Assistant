package com.meetmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MeetMindApplication {
    public static void main(String[] args) { SpringApplication.run(MeetMindApplication.class, args); }
}
