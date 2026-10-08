package com.ada.approval.config.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentation only; endpoint authorization remains in SecurityConfig. */
@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Approval Workflow REST API")
                                .description(
                                        "Session-authenticated API for leave requests, approvals and administration")
                                .version("1"));
    }

    @Bean
    public GroupedOpenApi restApi() {
        return GroupedOpenApi.builder()
                .group("rest-api")
                .packagesToScan("com.ada.approval.api.controller")
                .pathsToMatch("/api/**")
                .build();
    }
}
