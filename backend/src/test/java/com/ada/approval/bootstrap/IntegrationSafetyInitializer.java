package com.ada.approval.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** Integration-only, before bean creation: reject overrides that could reach another database. */
public final class IntegrationSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String JDBC_URL =
            "jdbc:mysql://127.0.0.1:3307/hpoa_integration"
                    + "?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of("integration"))) {
            throw new IllegalStateException(
                    "Integration initializer requires the integration profile");
        }
        require(environment, "spring.datasource.url", JDBC_URL);
        require(environment, "spring.datasource.username", "hpoa_integration");
        require(environment, "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        if (environment.containsProperty("spring.datasource.jndi-name")) {
            throw new IllegalStateException("JNDI datasource is not allowed in integration");
        }
        optionalMatch(environment, "spring.datasource.hikari.jdbc-url", JDBC_URL);
        optionalMatch(environment, "spring.datasource.hikari.username", "hpoa_integration");
        String secret = environment.getProperty("INTEGRATION_DB_PASSWORD");
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("INTEGRATION_DB_PASSWORD is required");
        }
        require(environment, "spring.datasource.password", secret);
        optionalMatch(environment, "spring.datasource.hikari.password", secret);
        // This initializer creates no beans and never obtains a JDBC connection.
    }

    private static void require(ConfigurableEnvironment environment, String key, String expected) {
        if (!expected.equals(environment.getProperty(key))) {
            throw new IllegalStateException("Unsafe integration configuration: " + key);
        }
    }

    private static void optionalMatch(
            ConfigurableEnvironment environment, String key, String expected) {
        if (environment.containsProperty(key)) require(environment, key, expected);
    }
}
