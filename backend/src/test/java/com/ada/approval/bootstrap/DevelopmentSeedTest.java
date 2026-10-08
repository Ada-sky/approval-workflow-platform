package com.ada.approval.bootstrap;

import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Compiles/runs only the seed tool's explicit database-free branch. */
class DevelopmentSeedTest {
    @TempDir Path classes;

    @Test
    void seedGuardInventoryComparisonAndBcryptRemainDatabaseFree() throws Exception {
        Path tools = Path.of("../tools/development").toAbsolutePath().normalize();
        int result =
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
                                tools.resolve("DevelopmentSeed.java").toString());
        assertEquals(0, result);
        try (var loader =
                new URLClassLoader(
                        new java.net.URL[] {classes.toUri().toURL()},
                        getClass().getClassLoader())) {
            var type = loader.loadClass("development.DevelopmentSeed");
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
            for (var invalid :
                    java.util.List.of(
                            java.util.List.<String>of(),
                            java.util.List.of("1"),
                            java.util.List.of("2"),
                            java.util.List.of("2", "1"),
                            java.util.List.of("1", "1"),
                            java.util.List.of("1", "2", "3"))) {
                var successes = java.util.Collections.nCopies(invalid.size(), true);
                assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        () -> migrations.invoke(null, invalid, successes));
            }
            for (var successes :
                    java.util.List.of(
                            java.util.List.of(false, true), java.util.List.of(true, false))) {
                assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        () -> migrations.invoke(null, java.util.List.of("1", "2"), successes));
            }
            var policy = type.getDeclaredMethod("validateRotationPassword", String.class);
            policy.setAccessible(true);
            assertDoesNotThrow(() -> policy.invoke(null, "A-valid-local-password"));
            assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> policy.invoke(null, "too-short"));
            assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> policy.invoke(null, "€".repeat(25)));
            assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> policy.invoke(null, " ".repeat(12)));
            String source = Files.readString(tools.resolve("DevelopmentSeed.java"));
            String rotation =
                    source.substring(
                            source.indexOf("static void rotate()"),
                            source.indexOf("public static void main"));
            assertTrue(rotation.contains("UPDATE t_account SET password=? WHERE id=?"));
            assertFalse(rotation.contains("INSERT "));
            assertFalse(rotation.contains("DELETE "));
            assertFalse(rotation.contains("update_time"));
            type.getMethod("main", String[].class)
                    .invoke(
                            null,
                            (Object)
                                    new String[] {
                                        "--self-test",
                                        tools.resolve("development-fixture.sql").toString()
                                    });
        }
    }

    @Test
    void fixtureHasNoPasswordsOrWorkflowWritesAndIsOutsideMigrations() throws Exception {
        String fixture = Files.readString(Path.of("../tools/development/development-fixture.sql"));
        assertFalse(fixture.contains("$2a$"));
        assertFalse(fixture.contains("$2b$"));
        assertFalse(fixture.contains("ACT_"));
        assertFalse(fixture.contains("t_holiday_apply"));
        assertFalse(fixture.contains("CREATE "));
        assertFalse(fixture.contains("DROP "));
        assertTrue(fixture.contains("@employee_a_hash"));
        assertTrue(fixture.contains("'General Manager'"));
        assertTrue(fixture.contains("'HR'"));
        assertFalse(fixture.codePoints().anyMatch(c -> c >= 0x3400 && c <= 0x9fff));
    }
}
