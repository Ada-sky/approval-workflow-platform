package com.ada.approval.bootstrap;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Dispatches startup safety checks before application beans initialize. Explicit initialization
 * profiles cannot bypass their target guard by removing the historical delegation property.
 */
public final class ConfiguredContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var profiles = java.util.Arrays.asList(context.getEnvironment().getActiveProfiles());
        if (profiles.stream().anyMatch(java.util.Set.of("docker", "docker-init")::contains)) {
            new DockerSafetyInitializer().initialize(context);
            return;
        }
        // Explicit initialization and regression profiles require exact-target checks.
        // Activate automatically so removing a delegation property cannot bypass their guard.
        if (profiles.stream()
                .anyMatch(java.util.Set.of("flyway-test", "flyway-retest", "dev-init")::contains)) {
            new FlywayTestSafetyInitializer().initialize(context);
        }
        // Ordinary dev startup is independent of historical initializer delegation.
        if (profiles.contains("dev")) return;
        String configured = context.getEnvironment().getProperty("context.initializer.classes");
        if (!StringUtils.hasText(configured)) return;
        for (String name : StringUtils.commaDelimitedListToStringArray(configured)) {
            try {
                Class<?> type = ClassUtils.forName(name.trim(), context.getClassLoader());
                if (type == ConfiguredContextInitializer.class
                        || !ApplicationContextInitializer.class.isAssignableFrom(type))
                    throw new IllegalStateException("Invalid context initializer: " + name);
                @SuppressWarnings("unchecked")
                ApplicationContextInitializer<ConfigurableApplicationContext> delegate =
                        (ApplicationContextInitializer<ConfigurableApplicationContext>)
                                BeanUtils.instantiateClass(type);
                delegate.initialize(context);
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Context initializer not found", exception);
            }
        }
    }
}
