package com.ada.approval.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

/** Never refreshes a context or creates datasource/engine beans. */
class IntegrationSafetyInitializerTest {
    private void delegateProfile(boolean unsafe) throws Exception {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty("INTEGRATION_DB_PASSWORD", "unit-test-placeholder");
        environment.setActiveProfiles("integration");
        for (org.springframework.core.env.PropertySource<?> source :
                new org.springframework.boot.env.YamlPropertySourceLoader()
                        .load(
                                "integration",
                                new org.springframework.core.io.ClassPathResource(
                                        "fixtures/profiles/application-integration.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        if (unsafe) environment.withProperty("spring.datasource.username", "root");
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            new ConfiguredContextInitializer().initialize(context);
            assertEquals(
                    IntegrationSafetyInitializer.JDBC_URL,
                    environment.getProperty("spring.datasource.url"));
            assertEquals(
                    "hr_employee_holiday.bpmn",
                    environment.getProperty(
                            "spring.activiti.process-definition-location-suffixes[0]"));
        }
    }

    @Test
    void profileRegistersPreBeanGuardWithResolvedEnvironmentPassword() {
        assertDoesNotThrow(() -> delegateProfile(false));
    }

    @Test
    void profileDelegationRejectsAnEffectiveOverride() {
        assertThrows(IllegalStateException.class, () -> delegateProfile(true));
    }
}
