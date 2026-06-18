package com.example.releasepulse;

// Import the SpringApplication runner class to execute the boot process
import org.springframework.boot.SpringApplication;
// Import the SpringBootApplication annotation to enable configuration scan and autowiring
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class to initialize and run the Spring Boot server.
 */
// Annotate class to designate it as the central configuration and bootstrap class
@SpringBootApplication
public class ReleasePulseApplication {
    // Standard execution entry point method called when launching the Java JVM
    public static void main(String[] args) {
        // Delegate bootstrap sequence to SpringBoot runner by passing class configuration and args
        SpringApplication.run(ReleasePulseApplication.class, args);
    }
}