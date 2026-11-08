package com.aiprivacy.shield;

import com.aiprivacy.shield.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PrivacyShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivacyShieldApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedDefaultUser(UserService userService) {
        return args -> {
            if (!userService.emailExists("admin@protectoshield.ai")) {
                userService.register("Security Admin", "admin@protectoshield.ai", "AdminPassword123!");
            }
        };
    }
}
