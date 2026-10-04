package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Web-Based Help Desk System (SE2030, group MLBB8G204).
 * Starts the embedded server on port 8080 with the H2 database from application.properties.
 */
@SpringBootApplication
public class HelpdeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpdeskApplication.class, args);
    }

}
