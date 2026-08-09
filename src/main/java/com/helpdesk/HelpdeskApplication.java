package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Web-Based Help Desk System.
 * SE2030 Software Engineering - Group MLBB8G204
 *
 * Running this class starts an embedded web server on http://localhost:8080
 * and connects to the in-memory H2 database (see application.properties).
 */
@SpringBootApplication
public class HelpdeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpdeskApplication.class, args);
    }

}
