package com.ada.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Starts the Spring Boot application. Application tables use JPA and Flyway; Activiti owns workflow
 * execution and its ACT_* schema.
 */
@SpringBootApplication
@EnableTransactionManagement
public class ApprovalWorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalWorkflowApplication.class, args);
    }
}
