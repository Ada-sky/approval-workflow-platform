package com.ada.approval.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** YAML/environment resolution only: no application, datasource or engine. */
class PublicationConfigurationTest {
    @Test
    void retiredCodeAndExampleProcessesAreNotProductionInputs() throws Exception {
        for (String name :
                new String[] {
                    "req/AccountParam",
                    "service/IHolidayTypeService",
                    "service/impl/HolidayTypeServiceImpl",
                    "bootstrap/IntegrationSafetyInitializer"
                }) {
            assertFalse(
                    java.nio.file.Files.exists(
                            java.nio.file.Path.of(
                                    "src/main/java/com/ada/approval/" + name + ".java")));
            assertFalse(
                    java.nio.file.Files.exists(
                            java.nio.file.Path.of(
                                    "target/classes/com/ada/approval/" + name + ".class")));
        }
        assertTrue(
                java.nio.file.Files.exists(
                        java.nio.file.Path.of(
                                "src/test/java/com/ada/approval/bootstrap/IntegrationSafetyInitializer.java")));
        assertTrue(new ClassPathResource("bpmn/hr_employee_holiday.bpmn").exists());
        for (String name :
                new String[] {
                    "employee_form.bpmn",
                    "employee_holiday.bpmn",
                    "employee_level.bpmn",
                    "employee_level2.bpmn"
                }) assertFalse(new ClassPathResource("bpmn/" + name).exists());
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var dependencies =
                factory.newDocumentBuilder().parse("pom.xml").getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = (org.w3c.dom.Element) dependencies.item(i);
            String group = dependency.getElementsByTagName("groupId").item(0).getTextContent();
            String artifact =
                    dependency.getElementsByTagName("artifactId").item(0).getTextContent();
            assertFalse(group.equals("junit") && artifact.equals("junit"));
        }
    }

    @Test
    void historicalProfilesAreExplicitFixturesAndActiveProfilesRemainRuntimeResources() {
        for (String profile : new String[] {"integration", "flyway-test", "flyway-retest"}) {
            String name = "application-" + profile + ".yml";
            assertFalse(
                    java.nio.file.Files.exists(java.nio.file.Path.of("src/main/resources", name)));
            assertFalse(new ClassPathResource(name).exists());
            assertTrue(new ClassPathResource("fixtures/profiles/" + name).exists());
        }
        for (String name :
                new String[] {
                    "application.yml",
                    "application-dev.yml",
                    "application-dev-init.yml",
                    "application-docker.yml",
                    "application-docker-init.yml"
                }) assertTrue(new ClassPathResource(name).exists());
    }

    private MockEnvironment base() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        for (var source :
                new YamlPropertySourceLoader()
                        .load("base", new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        return environment;
    }

    @Test
    void baseDatasourceRequiresExplicitValuesWithoutCredentialOrTargetFallbacks() throws Exception {
        var source =
                new YamlPropertySourceLoader()
                        .load("base", new ClassPathResource("application.yml"))
                        .get(0);
        assertEquals("${DB_URL}", source.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", source.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", source.getProperty("spring.datasource.password"));
    }

    @Test
    void missingEnvironmentValuesCannotResolveDefaultDatasource() throws Exception {
        MockEnvironment environment = base();
        for (String key :
                new String[] {
                    "spring.datasource.url",
                    "spring.datasource.username",
                    "spring.datasource.password"
                })
            assertThrows(
                    IllegalArgumentException.class, () -> environment.getRequiredProperty(key));
    }

    @Test
    void explicitEnvironmentValuesResolveWithoutStartingAnything() throws Exception {
        MockEnvironment environment = base();
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "synthetic-env",
                                Map.of(
                                        "DB_URL",
                                        "jdbc:mysql://example.invalid/fixture",
                                        "DB_USERNAME",
                                        "synthetic-user",
                                        "DB_PASSWORD",
                                        "synthetic-value")));
        assertEquals(
                "jdbc:mysql://example.invalid/fixture",
                environment.getProperty("spring.datasource.url"));
        assertEquals("synthetic-user", environment.getProperty("spring.datasource.username"));
        assertEquals("synthetic-value", environment.getProperty("spring.datasource.password"));
    }

    private MockEnvironment dev() throws Exception {
        var env = base();
        env.setActiveProfiles("dev");
        for (var source :
                new YamlPropertySourceLoader()
                        .load("dev", new ClassPathResource("application-dev.yml")))
            env.getPropertySources().addFirst(source);
        return env;
    }

    @Test
    void normalDevRequiresOnlyConventionalDatasourceEnvironment() throws Exception {
        var env = dev();
        env.withProperty("DB_URL", "jdbc:mysql://127.0.0.1:3306/hpoa_dev")
                .withProperty("DB_USERNAME", "hpoa_dev")
                .withProperty("DB_PASSWORD", "synthetic-local-value");
        for (String prefix : new String[] {"spring.datasource.", "spring.datasource.hikari."}) {
            assertEquals(
                    "jdbc:mysql://127.0.0.1:3306/hpoa_dev",
                    env.getProperty(prefix + (prefix.contains("hikari") ? "jdbc-url" : "url")));
            assertEquals("hpoa_dev", env.getProperty(prefix + "username"));
            assertEquals("synthetic-local-value", env.getProperty(prefix + "password"));
        }
        assertFalse(env.containsProperty("DEV_DB_PASSWORD"));
        assertFalse(env.containsProperty("context.initializer.classes"));
        for (String key :
                new String[] {
                    "spring.datasource.url",
                    "spring.datasource.username",
                    "spring.datasource.password"
                })
            assertThrows(IllegalArgumentException.class, () -> dev().getRequiredProperty(key));
    }

    @Test
    void normalDevKeepsSchemaAndWorkflowInitializationDisabled() throws Exception {
        var env = dev();
        for (String key :
                new String[] {
                    "spring.jpa.generate-ddl",
                    "spring.flyway.baseline-on-migrate",
                    "spring.activiti.database-schema-update",
                    "spring.activiti.check-process-definitions",
                    "spring.activiti.async-executor-activate"
                }) assertEquals("false", env.getProperty(key), key);
        for (String key :
                new String[] {
                    "spring.flyway.enabled",
                    "spring.flyway.validate-on-migrate",
                    "spring.flyway.clean-disabled"
                }) assertEquals("true", env.getProperty(key), key);
        assertEquals(
                "classpath:db/migration/application", env.getProperty("spring.flyway.locations"));
        assertEquals("none", env.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(
                "none",
                env.getProperty(
                        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action"));
        assertEquals("never", env.getProperty("spring.sql.init.mode"));
        assertEquals("127.0.0.1", env.getProperty("server.address"));
        assertEquals("8080", env.getProperty("server.port"));
        assertFalse(env.containsProperty("spring.datasource.initialization-mode"));
    }

    @Test
    void normalDevDoesNotInvokeMigrationGuardsOrHistoricalDelegates() throws Exception {
        var env = dev();
        // No password or resolved datasource: this adapter must do nothing for normal dev.
        env.withProperty(
                "context.initializer.classes", FlywayTestSafetyInitializer.class.getName());
        try (var context = new org.springframework.context.support.GenericApplicationContext()) {
            context.setEnvironment(env);
            assertDoesNotThrow(() -> new ConfiguredContextInitializer().initialize(context));
            assertFalse(context.isActive());
            assertEquals(0, context.getBeanFactory().getBeanDefinitionCount());
        }
    }

    @Test
    void normalDevCannotBypassAnExplicitDedicatedProfileGuard() throws Exception {
        var env = dev();
        env.setActiveProfiles("dev", "dev-init");
        try (var context = new org.springframework.context.support.GenericApplicationContext()) {
            context.setEnvironment(env);
            assertThrows(
                    IllegalStateException.class,
                    () -> new ConfiguredContextInitializer().initialize(context));
        }
    }
}
