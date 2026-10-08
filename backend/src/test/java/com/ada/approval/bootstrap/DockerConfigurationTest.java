package com.ada.approval.bootstrap;

import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.support.GenericApplicationContext;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DockerConfigurationTest {
    @TempDir Path classes;

    private GenericApplicationContext context(String profile) throws Exception {
        var context = new GenericApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "secret",
                                Map.of("DOCKER_DB_PASSWORD", "database-free-placeholder")));
        for (var source :
                new YamlPropertySourceLoader()
                        .load("docker", new ClassPathResource("application-" + profile + ".yml")))
            context.getEnvironment().getPropertySources().addLast(source);
        return context;
    }

    @Test
    void bothProfilesPassWithoutStartingBeans() throws Exception {
        for (String profile : new String[] {"docker", "docker-init"})
            try (var c = context(profile)) {
                assertDoesNotThrow(() -> new ConfiguredContextInitializer().initialize(c));
                assertFalse(c.isActive());
            }
    }

    @Test
    void rejectsHostTargets() throws Exception {
        for (String url :
                new String[] {
                    "jdbc:mysql://127.0.0.1:3306/hpoa_dev",
                    "jdbc:mysql://127.0.0.1:3306/hp",
                    "jdbc:mysql://127.0.0.1:3307/hpoa_integration"
                })
            try (var c = context("docker")) {
                c.getEnvironment()
                        .getPropertySources()
                        .addFirst(
                                new MapPropertySource("bad", Map.of("spring.datasource.url", url)));
                assertThrows(
                        IllegalStateException.class,
                        () -> new DockerSafetyInitializer().initialize(c));
            }
    }

    @Test
    void rejectsUnsafeOverridesAndMixedProfiles() throws Exception {
        Map<String, String> overrides =
                Map.of(
                        "spring.flyway.clean-disabled",
                        "false",
                        "spring.jpa.hibernate.ddl-auto",
                        "update",
                        "spring.activiti.database-schema-update",
                        "true",
                        "spring.datasource.username",
                        "root",
                        "spring.flyway.url",
                        "jdbc:mysql://other/db",
                        "DOCKER_DB_PASSWORD",
                        "");
        for (var bad : overrides.entrySet())
            try (var c = context("docker")) {
                c.getEnvironment()
                        .getPropertySources()
                        .addFirst(
                                new MapPropertySource("bad", Map.of(bad.getKey(), bad.getValue())));
                assertThrows(
                        IllegalStateException.class,
                        () -> new DockerSafetyInitializer().initialize(c));
            }
        try (var c = context("docker")) {
            c.getEnvironment().setActiveProfiles("docker", "dev");
            assertThrows(
                    IllegalStateException.class,
                    () -> new ConfiguredContextInitializer().initialize(c));
        }
    }

    @Test
    void composeHasIsolatedStorageAndOptInOperations() throws Exception {
        String s = Files.readString(Path.of("../compose.yaml"));
        for (String expected :
                new String[] {
                    "127.0.0.1:8081:80",
                    "mysql:8.0.44",
                    "profiles: [setup]",
                    "profiles: [demo]",
                    "mysql-data:/var/lib/mysql"
                }) assertTrue(s.contains(expected));
        for (String forbidden : new String[] {"hpoa_dev", "integration/", "3306:3306"})
            assertFalse(s.contains(forbidden));
    }

    @Test
    void seedSelfTestRunsWithoutJdbc() throws Exception {
        assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler()
                        .run(
                                null,
                                null,
                                null,
                                "-encoding",
                                "UTF-8",
                                "-classpath",
                                System.getProperty("java.class.path"),
                                "-d",
                                classes.toString(),
                                "../tools/docker/DockerDemoSeed.java"));
        try (var loader =
                new URLClassLoader(
                        new java.net.URL[] {classes.toUri().toURL()},
                        getClass().getClassLoader())) {
            var type = loader.loadClass("development.DockerDemoSeed");
            type.getMethod("main", String[].class)
                    .invoke(
                            null,
                            (Object)
                                    new String[] {
                                        "--self-test",
                                        "../tools/development/development-fixture.sql"
                                    });
            var target = type.getDeclaredMethod("target", String.class, String.class);
            target.setAccessible(true);
            assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> target.invoke(null, "jdbc:mysql://127.0.0.1:3306/hpoa_dev", "hpoa_dev"));
            var migrations =
                    type.getDeclaredMethod(
                            "validateMigrations", java.util.List.class, java.util.List.class);
            migrations.setAccessible(true);
            assertDoesNotThrow(
                    () ->
                            migrations.invoke(
                                    null,
                                    java.util.List.of("1", "2"),
                                    java.util.List.of(true, true)));
            for (var versions :
                    java.util.List.of(
                            java.util.List.<String>of(),
                            java.util.List.of("1"),
                            java.util.List.of("2"),
                            java.util.List.of("2", "1"),
                            java.util.List.of("1", "1"),
                            java.util.List.of("1", "2", "3")))
                assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        () ->
                                migrations.invoke(
                                        null,
                                        versions,
                                        java.util.Collections.nCopies(versions.size(), true)));
            for (var successes :
                    java.util.List.of(
                            java.util.List.of(false, true), java.util.List.of(true, false)))
                assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        () -> migrations.invoke(null, java.util.List.of("1", "2"), successes));
        }
    }

    @Test
    void imageBuildPreservesTestToolsAndExcludesEvidence() throws Exception {
        String s = Files.readString(Path.of("Dockerfile"));
        assertTrue(s.contains("COPY tools/development tools/development"));
        assertTrue(s.contains("USER 10001:10001"));
        s = Files.readString(Path.of("../.dockerignore"));
        assertTrue(s.contains("**/.local"));
        assertTrue(s.contains("integration"));
    }
}
