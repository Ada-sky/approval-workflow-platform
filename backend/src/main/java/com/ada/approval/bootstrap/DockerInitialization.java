package com.ada.approval.bootstrap;

import com.ada.approval.ApprovalWorkflowApplication;
import org.springframework.boot.SpringApplication;

/**
 * Runs the explicit Docker schema-initialization entry point and closes its application context.
 * Flyway creates application tables; Activiti initializes its own schema. Normal Docker startup is
 * separate.
 */
public final class DockerInitialization {
    public static void main(String[] args) {
        if (!"docker-init".equals(System.getenv("SPRING_PROFILES_ACTIVE")))
            throw new IllegalStateException("Explicit docker-init profile required");
        SpringApplication app = new SpringApplication(ApprovalWorkflowApplication.class);
        try (var context = app.run(args)) {
            System.out.println("DOCKER_INITIALIZATION_COMPLETE");
        }
    }
}
