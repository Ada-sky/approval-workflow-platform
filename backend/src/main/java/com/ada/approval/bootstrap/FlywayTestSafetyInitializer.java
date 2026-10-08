package com.ada.approval.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * Validates exact datasource targets and schema-management settings before initialization. Supports
 * explicit development initialization and historical Flyway regression profiles without opening a
 * database connection.
 */
public final class FlywayTestSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    public static final String JDBC_URL =
            "jdbc:mysql://127.0.0.1:3307/hpoa_flyway_test"
                    + "?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai";
    public static final String RETEST_JDBC_URL =
            JDBC_URL.replace("/hpoa_flyway_test?", "/hpoa_flyway_retest?");

    public static final String DEV_JDBC_URL = "jdbc:mysql://127.0.0.1:3306/hpoa_dev";

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        boolean retest = java.util.Arrays.asList(env.getActiveProfiles()).contains("flyway-retest");
        boolean devInit = java.util.Arrays.asList(env.getActiveProfiles()).contains("dev-init");
        boolean dev = devInit;
        boolean profile =
                dev
                        || retest
                        || java.util.Arrays.asList(env.getActiveProfiles()).contains("flyway-test");
        // Boot enables Flyway when this property is absent; do not let omission bypass the guard.
        boolean enabled = env.getProperty("spring.flyway.enabled", Boolean.class, true);
        if (!profile && !enabled) return;
        if (!profile || env.getActiveProfiles().length != 1 || !enabled) {
            throw new IllegalStateException(
                    "Flyway requires one dedicated guarded profile and explicit enablement");
        }
        String expectedUrl = dev ? DEV_JDBC_URL : retest ? RETEST_JDBC_URL : JDBC_URL;
        String expectedUser = dev ? "hpoa_dev" : retest ? "hpoa_flyway_retest" : "hpoa_flyway_test";
        require(env, "spring.datasource.url", expectedUrl);
        require(env, "spring.datasource.hikari.jdbc-url", expectedUrl);
        require(env, "spring.datasource.username", expectedUser);
        require(env, "spring.datasource.hikari.username", expectedUser);
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
        if (dev) {
            require(env, "server.address", "127.0.0.1");
            require(env, "server.port", "8080");
            require(env, "spring.activiti.database-schema-update", "true");
            require(env, "spring.activiti.check-process-definitions", "true");
            require(env, "spring.activiti.process-definition-location-prefix", "classpath:/bpmn/");
            require(
                    env,
                    "spring.activiti.process-definition-location-suffixes[0]",
                    "hr_employee_holiday.bpmn");
            require(env, "spring.activiti.async-executor-activate", "false");
            require(env, "spring.activiti.db-history-used", "true");
            require(env, "spring.activiti.history-level", "full");
            if (env.containsProperty("spring.activiti.process-definition-location-suffixes[1]"))
                throw new IllegalStateException("Additional BPMN resources forbidden");
        }
        String secret = env.getProperty(dev ? "DEV_DB_PASSWORD" : "FLYWAY_TEST_DB_PASSWORD");
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("Required database credential is missing");
        require(env, "spring.datasource.password", secret);
        require(env, "spring.datasource.hikari.password", secret);
        // No refresh, datasource, Flyway invocation, engine or JDBC connection.
    }

    private static void require(ConfigurableEnvironment env, String key, String expected) {
        if (!expected.equals(env.getProperty(key)))
            throw new IllegalStateException("Unsafe Flyway test configuration: " + key);
    }
}
