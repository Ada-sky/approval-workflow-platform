package com.ada.approval.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * Rejects Docker profiles that target anything other than the isolated Compose datasource. Normal
 * runtime and explicit initialization have different Activiti schema/deployment requirements.
 */
public final class DockerSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String JDBC_URL = "jdbc:mysql://db:3306/hpoa_docker";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        String[] profiles = env.getActiveProfiles();
        if (profiles.length != 1
                || !(profiles[0].equals("docker") || profiles[0].equals("docker-init")))
            throw new IllegalStateException("Exactly one Docker profile required");
        boolean init = profiles[0].equals("docker-init");
        require(env, "spring.flyway.enabled", "true");
        require(env, "spring.datasource.url", JDBC_URL);
        require(env, "spring.datasource.hikari.jdbc-url", JDBC_URL);
        require(env, "spring.datasource.username", "hpoa_docker");
        require(env, "spring.datasource.hikari.username", "hpoa_docker");
        require(env, "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        require(env, "spring.flyway.locations", "classpath:db/migration/application");
        require(env, "spring.flyway.baseline-on-migrate", "false");
        require(env, "spring.flyway.validate-on-migrate", "true");
        require(env, "spring.flyway.clean-disabled", "true");
        require(env, "spring.sql.init.mode", "never");
        require(env, "spring.jpa.generate-ddl", "false");
        require(env, "spring.jpa.hibernate.ddl-auto", "none");
        require(
                env,
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
                "none");
        for (String key :
                new String[] {
                    "spring.datasource.jndi-name",
                    "spring.datasource.type",
                    "spring.datasource.hikari.data-source-class-name",
                    "spring.datasource.hikari.data-source-jndi",
                    "spring.datasource.hikari.catalog",
                    "spring.datasource.hikari.schema",
                    "spring.datasource.hikari.connection-init-sql",
                    "spring.flyway.url",
                    "spring.flyway.user",
                    "spring.flyway.password",
                    "spring.flyway.schemas",
                    "spring.flyway.default-schema",
                    "spring.flyway.init-sqls"
                }) {
            if (env.containsProperty(key))
                throw new IllegalStateException(
                        "Alternate Flyway/datasource configuration forbidden: " + key);
        }
        for (org.springframework.core.env.PropertySource<?> source : env.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?>) {
                for (String key : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    String normalized =
                            key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if (normalized.startsWith("springdatasourcehikaridatasourceproperties")
                            || normalized.startsWith("springflywayjdbcproperties")
                            || normalized.startsWith("springflywayschemas")
                            || normalized.startsWith("springflywayinitsqls")
                            || (normalized.startsWith("springflywaylocations")
                                    && !normalized.equals("springflywaylocations"))) {
                        throw new IllegalStateException("Alternate JDBC properties forbidden");
                    }
                }
            }
        }
        require(env, "server.address", "0.0.0.0");
        require(env, "server.port", "8080");
        require(env, "spring.activiti.database-schema-update", Boolean.toString(init));
        require(env, "spring.activiti.check-process-definitions", Boolean.toString(init));
        require(env, "spring.activiti.process-definition-location-prefix", "classpath:/bpmn/");
        require(
                env,
                "spring.activiti.process-definition-location-suffixes[0]",
                "hr_employee_holiday.bpmn");
        if (env.containsProperty("spring.activiti.process-definition-location-suffixes[1]"))
            throw new IllegalStateException("Additional BPMN resources forbidden");
        require(env, "spring.activiti.async-executor-activate", "false");
        require(env, "spring.activiti.db-history-used", "true");
        require(env, "spring.activiti.history-level", "full");
        String secret = env.getProperty("DOCKER_DB_PASSWORD");
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("Docker database credential required");
        require(env, "spring.datasource.password", secret);
        require(env, "spring.datasource.hikari.password", secret);
    }

    private static void require(ConfigurableEnvironment env, String key, String expected) {
        if (!expected.equals(env.getProperty(key)))
            throw new IllegalStateException("Unsafe Docker configuration: " + key);
    }
}
