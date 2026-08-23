package dev.reclaim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReclaimApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReclaimApplication.class, args);
    }
}
