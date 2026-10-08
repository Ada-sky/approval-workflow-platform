package com.ada.approval.migration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;
import org.activiti.engine.impl.util.ReflectUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Resource checks only: never creates a Spring context, engine or JDBC connection. */
class ActivitiMysqlResourceTest {
    private static final String RESOURCE =
            "org/activiti/db/create/activiti.mysql.create.history.sql";
    private static final String INVALID = "DETAILS_ varbinary(max)";
    private static final String CORRECT = "DETAILS_ LONGBLOB";

    private static byte[] upstream(String resource) throws Exception {
        Path jar =
                Path.of(
                        ReflectUtil.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        assertEquals(
                "activiti-engine-8.1.0.jar",
                jar.getFileName().toString(),
                "Reassess/remove the compatibility override when changing Activiti");
        try (JarFile archive = new JarFile(jar.toFile());
                var input = archive.getInputStream(archive.getJarEntry(resource))) {
            return input.readAllBytes();
        }
    }

    private static byte[] selected() throws Exception {
        try (var input = ReflectUtil.getResourceAsStream(RESOURCE)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    @Test
    void actualActivitiResourceLookupSelectsApplicationCopy() throws Exception {
        var url = ReflectUtil.getResource(RESOURCE);
        assertNotNull(url);
        assertEquals("file", url.getProtocol());
        assertTrue(url.toExternalForm().contains("/target/classes/"), url.toString());
        try (var input =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input);
            assertArrayEquals(selected(), input.readAllBytes());
        }
    }

    @Test
    void entireResourceIsByteIdenticalExceptTheApprovedType() throws Exception {
        byte[] original = upstream(RESOURCE);
        assertEquals(
                "26852285c8e54370cb0350c02a2a8c795cc4175dc88122cf17ca33cb125ed14a",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
        String text = new String(original, StandardCharsets.UTF_8);
        assertEquals(1, text.split(java.util.regex.Pattern.quote(INVALID), -1).length - 1);
        assertArrayEquals(
                text.replace(INVALID, CORRECT).getBytes(StandardCharsets.UTF_8), selected());
    }

    @Test
    void exactlyOneSqlStatementChangesAndItIsTheHistoricIdentityLinkColumn() throws Exception {
        String[] original = new String(upstream(RESOURCE), StandardCharsets.UTF_8).split(";", -1);
        String corrected = new String(selected(), StandardCharsets.UTF_8);
        String[] statements = corrected.split(";", -1);
        assertEquals(original.length, statements.length);
        int differences = 0;
        for (int i = 0; i < statements.length; i++) {
            if (!original[i].equals(statements[i])) {
                differences++;
                assertTrue(statements[i].contains("create table ACT_HI_IDENTITYLINK ("));
                assertEquals(original[i].replace(INVALID, CORRECT), statements[i]);
                assertTrue(statements[i].contains(CORRECT));
            }
        }
        assertEquals(1, differences);
        assertFalse(corrected.contains(INVALID));
    }

    @Test
    void correctedTypeAgreesWithTheUnmodifiedMysqlUpgradeScripts() throws Exception {
        for (String component : new String[] {"engine", "history"}) {
            String resource =
                    "org/activiti/db/upgrade/activiti.mysql.upgradestep.800.to.810."
                            + component
                            + ".sql";
            assertEquals(
                    "alter table ACT_"
                            + (component.equals("engine") ? "RU" : "HI")
                            + "_IDENTITYLINK add column DETAILS_ LONGBLOB;",
                    new String(upstream(resource), StandardCharsets.UTF_8).trim());
        }
    }
}
