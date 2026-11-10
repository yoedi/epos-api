package com.yoedi.epos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// The default in-memory user is excluded; authentication is JWT-based (stage 3).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class EposApplication {

    public static void main(String[] args) {
        SpringApplication.run(EposApplication.class, args);
    }
}
