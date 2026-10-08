package com.ada.approval.bootstrap;

import com.ada.approval.ApprovalWorkflowApplication;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.support.GenericApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/** No refreshed context, Flyway instance, datasource, engine or database connection. */
class FlywayStage1Test {
    private static final String SECRET = "database-free-placeholder";

    private MockEnvironment valid() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty("FLYWAY_TEST_DB_PASSWORD", SECRET);
        env.setActiveProfiles("flyway-test");
        for (var source :
                new YamlPropertySourceLoader()
                        .load(
                                "profile",
                                new ClassPathResource(
                                        "fixtures/profiles/application-flyway-test.yml")))
            env.getPropertySources().addLast(source);
        return env;
    }

    private void check(MockEnvironment env) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(env);
            new FlywayTestSafetyInitializer().initialize(context);
            assertFalse(context.isActive());
            assertEquals(0, context.getBeanFactory().getBeanDefinitionCount());
        }
    }

    private void reject(String key, String value) throws Exception {
        var exception =
                assertThrows(
                        IllegalStateException.class, () -> check(valid().withProperty(key, value)));
        assertFalse(exception.getMessage().contains(SECRET));
    }

    @Test
    void archiveMigrationAddsOnlyApplicationVisibilityWithExistingRowsVisible() throws Exception {
        String sql =
                new ClassPathResource(
                                "db/migration/application/V2__add_applicant_archive_visibility.sql")
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                        .replaceAll("(?m)^--.*$", "")
                        .trim();
        assertEquals(
                "ALTER TABLE t_holiday_apply ADD COLUMN applicant_archived BOOLEAN NOT NULL DEFAULT FALSE;",
                sql);
        var field =
                com.ada.approval.entity.HolidayApply.class.getDeclaredField("applicantArchived");
        assertEquals(
                "applicant_archived", field.getAnnotation(jakarta.persistence.Column.class).name());
        assertFalse(field.getAnnotation(jakarta.persistence.Column.class).nullable());
        assertFalse(new com.ada.approval.entity.HolidayApply().isApplicantArchived());
    }

    @Test
    void migrationIsExactlyTheVerifiedTwelveTableDdl() throws Exception {
        String sql =
                new ClassPathResource("db/migration/application/V1__create_application_schema.sql")
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String original =
                new ClassPathResource("fixtures/schema/verified-application-schema.sql")
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(
                original.substring(original.indexOf("CREATE TABLE")).trim(),
                sql.substring(sql.indexOf("CREATE TABLE")).trim());
        Set<String> expected =
                Set.of(
                        "t_account",
                        "t_role",
                        "t_account_role",
                        "t_permission",
                        "t_menu",
                        "t_employee",
                        "t_dept",
                        "t_title_category",
                        "t_employee_status",
                        "t_holiday_apply",
                        "t_holiday_approval",
                        "t_holiday_type");
        Matcher matcher = Pattern.compile("CREATE TABLE `([^`]+)`").matcher(sql);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) tables.add(matcher.group(1));
        assertEquals(12, tables.size());
        assertEquals(expected, new HashSet<>(tables));
        String statements = sql.replaceAll("(?m)^--.*$", "");
        assertFalse(
                Pattern.compile(
                                "(?i)\\b(ACT_\\w*|ALTER|DROP|INSERT|UPDATE|DELETE|PREPARE|EXECUTE|FOREIGN|UNIQUE)\\b|CREATE\\s+(DATABASE|USER)|IF\\s+NOT\\s+EXISTS")
                        .matcher(statements)
                        .find());
        assertEquals(12, Arrays.stream(statements.split(";")).filter(s -> !s.isBlank()).count());
        assertFalse(statements.contains("3307"));
        assertFalse(statements.contains("hpoa_integration"));
    }

    private MockEnvironment dev() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty("DEV_DB_PASSWORD", SECRET);
        env.setActiveProfiles("dev-init");
        for (var source :
                new YamlPropertySourceLoader()
                        .load("dev", new ClassPathResource("application-dev-init.yml")))
            env.getPropertySources().addLast(source);
        return env;
    }

    @Test
    void devTargetPassesWithoutConnections() throws Exception {
        check(dev());
    }

    @Test
    void devRejectsOtherTargetsUsersAndUnsafeSettings() throws Exception {
        for (String url :
                List.of(
                        "jdbc:mysql://127.0.0.1:3306/hp",
                        "jdbc:mysql://127.0.0.1:3307/hpoa_dev",
                        "jdbc:mysql://localhost:3306/hpoa_dev"))
            assertThrows(
                    IllegalStateException.class,
                    () -> check(dev().withProperty("spring.datasource.url", url)));
        for (String key :
                List.of("spring.datasource.username", "spring.datasource.hikari.username"))
            assertThrows(IllegalStateException.class, () -> check(dev().withProperty(key, "root")));
        assertThrows(
                IllegalStateException.class,
                () -> check(dev().withProperty("spring.flyway.clean-disabled", "false")));
        assertThrows(
                IllegalStateException.class,
                () ->
                        check(
                                dev().withProperty(
                                                "spring.activiti.process-definition-location-suffixes[1]",
                                                "employee_holiday.bpmn")));
        assertThrows(
                IllegalStateException.class,
                () -> check(dev().withProperty("DEV_DB_PASSWORD", "")));
    }

    private MockEnvironment retest() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty("FLYWAY_TEST_DB_PASSWORD", SECRET);
        env.setActiveProfiles("flyway-retest");
        for (var source :
                new YamlPropertySourceLoader()
                        .load(
                                "retest",
                                new ClassPathResource(
                                        "fixtures/profiles/application-flyway-retest.yml")))
            env.getPropertySources().addLast(source);
        return env;
    }

    @Test
    void dedicatedProfilesCannotBeMixedOrSwapped() throws Exception {
        var mixed = retest();
        mixed.setActiveProfiles("flyway-test", "flyway-retest");
        assertThrows(IllegalStateException.class, () -> check(mixed));
        var swapped = retest();
        swapped.setActiveProfiles("flyway-test");
        assertThrows(IllegalStateException.class, () -> check(swapped));
        assertThrows(
                IllegalStateException.class,
                () ->
                        check(
                                valid().withProperty(
                                                "spring.datasource.url",
                                                FlywayTestSafetyInitializer.RETEST_JDBC_URL)));
    }

    @Test
    void guardIsNotGloballyRegisteredForNormalApplications() {
        var initializers =
                new SpringApplication(ApprovalWorkflowApplication.class).getInitializers();
        assertFalse(initializers.stream().anyMatch(i -> i instanceof FlywayTestSafetyInitializer));
        assertTrue(initializers.stream().anyMatch(i -> i instanceof ConfiguredContextInitializer));
    }

    @Test
    void dedicatedProfilesAutomaticallyActivateGuardBeforeBeanCreation() throws Exception {
        for (var env : List.of(valid(), retest(), dev())) {
            try (var context = new GenericApplicationContext()) {
                context.setEnvironment(env);
                new ConfiguredContextInitializer().initialize(context);
                assertFalse(context.isActive());
                assertEquals(0, context.getBeanFactory().getBeanDefinitionCount());
                env.withProperty("spring.datasource.username", "root");
                assertThrows(
                        IllegalStateException.class,
                        () -> new ConfiguredContextInitializer().initialize(context));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "hp",
                "hpoa_integration",
                "hpoa_migration_stage2",
                "hpoa_stage2_active7",
                "hpoa_stage2_active8",
                "arbitrary_database"
            })
    void rejectsEveryOtherDatabase(String database) throws Exception {
        reject(
                "spring.datasource.url",
                FlywayTestSafetyInitializer.JDBC_URL.replace(
                        "/hpoa_flyway_test?", "/" + database + "?"));
    }

    @Test
    void rejectsPort3306() throws Exception {
        reject(
                "spring.datasource.url",
                FlywayTestSafetyInitializer.JDBC_URL.replace(":3307/", ":3306/"));
    }

    @Test
    void rejectsLocalhostAlias() throws Exception {
        reject(
                "spring.datasource.url",
                FlywayTestSafetyInitializer.JDBC_URL.replace("127.0.0.1", "localhost"));
    }

    @Test
    void rejectsHikariDatabaseOverride() throws Exception {
        reject("spring.datasource.hikari.jdbc-url", "jdbc:mysql://127.0.0.1:3307/hpoa_integration");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.username", "spring.datasource.hikari.username"})
    void rejectsAlternateUser(String key) throws Exception {
        reject(key, "root");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
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
                "spring.flyway.schemas[0]",
                "spring.flyway.default-schema",
                "spring.flyway.init-sqls",
                "spring.flyway.init-sqls[0]",
                "spring.flyway.locations[0]",
                "spring.datasource.hikari.data-source-properties.url",
                "spring.flyway.jdbc-properties.url",
                "SPRING_FLYWAY_JDBC_PROPERTIES_URL"
            })
    void rejectsAlternateConnectionPaths(String key) throws Exception {
        reject(key, "override");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "spring.datasource.password",
                "spring.datasource.hikari.password",
                "FLYWAY_TEST_DB_PASSWORD"
            })
    void rejectsSecretMismatchOrAbsence(String key) throws Exception {
        reject(key, "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.flyway.clean-disabled", "spring.flyway.validate-on-migrate"})
    void rejectsDisabledSafetySetting(String key) throws Exception {
        reject(key, "false");
    }

    @Test
    void rejectsAutomaticBaseline() throws Exception {
        reject("spring.flyway.baseline-on-migrate", "true");
    }

    @Test
    void rejectsOtherMigrationLocation() throws Exception {
        reject("spring.flyway.locations", "classpath:db/migration");
    }

    @Test
    void rejectsHibernateCreation() throws Exception {
        reject("spring.jpa.hibernate.ddl-auto", "create");
    }

    @Test
    void rejectsJpaSchemaGeneration() throws Exception {
        reject(
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
                "create");
    }

    @Test
    void rejectsJpaDdlFlag() throws Exception {
        reject("spring.jpa.generate-ddl", "true");
    }

    @Test
    void rejectsSqlBootstrap() throws Exception {
        reject("spring.sql.init.mode", "always");
    }

    @Test
    void rejectsMixedProfiles() throws Exception {
        var e = valid();
        e.setActiveProfiles("flyway-test", "integration");
        assertThrows(IllegalStateException.class, () -> check(e));
    }

    @Test
    void rejectsFlywayActivationOutsideDedicatedProfile() throws Exception {
        var e = valid();
        e.setActiveProfiles("integration");
        assertThrows(IllegalStateException.class, () -> check(e));
    }

    @Test
    void dedicatedProfileRequiresExplicitEnablement() throws Exception {
        reject("spring.flyway.enabled", "false");
    }

    @Test
    void missingBaseConfigurationCannotBypassGuardThroughBootDefaultEnablement() {
        assertThrows(IllegalStateException.class, () -> check(new MockEnvironment()));
    }

    @Test
    void explicitlyDisabledFlywayNeedsNoDatasourceOrSecret() {
        check(new MockEnvironment().withProperty("spring.flyway.enabled", "false"));
    }

    @Test
    void existingEnvironmentsRemainDisabled() throws Exception {
        for (String name : List.of("normal", "integration", "migration-stage2")) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(name);
            var loader = new YamlPropertySourceLoader();
            if (name.equals("integration"))
                for (var s :
                        loader.load(
                                name,
                                new ClassPathResource(
                                        "fixtures/profiles/application-integration.yml")))
                    env.getPropertySources().addLast(s);
            if (name.equals("migration-stage2"))
                for (var s :
                        loader.load(
                                name,
                                new ClassPathResource(
                                        "fixtures/profiles/application-migration-stage2.yml")))
                    env.getPropertySources().addLast(s);
            for (var s : loader.load("base", new ClassPathResource("application.yml")))
                env.getPropertySources().addLast(s);
            assertFalse(env.getProperty("spring.flyway.enabled", Boolean.class));
            assertTrue(env.getProperty("spring.flyway.clean-disabled", Boolean.class));
            check(env);
        }
    }
}
