package com.ada.approval.migration;

import com.ada.approval.ApprovalWorkflowApplication;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.jpa.repository.Query;
import java.sql.Connection;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** No Boot datasource/engine auto-configuration and no JDBC connections. */
class PlatformMigrationTest {
    @Test
    void springSixMissingResourceKeepsTheApi404Contract() {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI("/api/departments/missing/path");
        var response =
                com.ada.approval.api.error.ApiExceptionHandler.fallback(
                        new org.springframework.web.servlet.resource.NoResourceFoundException(
                                org.springframework.http.HttpMethod.GET,
                                "api/departments/missing/path"),
                        request);
        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("/api/departments/missing/path", response.getBody().getPath());
    }

    private static final String[] ENTITIES = {
        "Account",
        "AccountRole",
        "Dept",
        "Employee",
        "EmployeeStatus",
        "HolidayApply",
        "HolidayApproval",
        "HolidayType",
        "Menu",
        "Permission",
        "Role",
        "TitleCategory"
    };

    public static final class NoConnections implements ConnectionProvider {
        static final AtomicInteger attempts = new AtomicInteger();

        public Connection getConnection() {
            attempts.incrementAndGet();
            throw new AssertionError("Database forbidden");
        }

        public void closeConnection(Connection connection) {
            throw new AssertionError("Database forbidden");
        }

        public boolean supportsAggressiveRelease() {
            return false;
        }

        public boolean isUnwrappableAs(Class<?> type) {
            return type.isInstance(this);
        }

        public <T> T unwrap(Class<T> type) {
            return type.cast(this);
        }
    }

    @Test
    void allMappingsAndRepositoryQueriesValidateWithoutConnections() throws Exception {
        NoConnections.attempts.set(0);
        StandardServiceRegistry registry =
                new StandardServiceRegistryBuilder()
                        .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                        .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                        .applySetting("hibernate.hbm2ddl.auto", "none")
                        .applySetting(
                                "jakarta.persistence.schema-generation.database.action", "none")
                        .applySetting("hibernate.connection.provider_class", new NoConnections())
                        .build();
        int jpql = 0, nativeSql = 0, derived = 0;
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (String name : ENTITIES)
                sources.addAnnotatedClass(Class.forName("com.ada.approval.entity." + name));
            try (SessionFactory factory = sources.buildMetadata().buildSessionFactory();
                    Session session = factory.openSession()) {
                for (String name : ENTITIES) {
                    Class<?> entity = Class.forName("com.ada.approval.entity." + name);
                    for (Method method :
                            Class.forName("com.ada.approval.repository." + name + "Repository")
                                    .getDeclaredMethods()) {
                        Query query = method.getAnnotation(Query.class);
                        if (query != null) {
                            if (query.nativeQuery()) {
                                session.createNativeQuery(query.value());
                                nativeSql++;
                            } else {
                                session.createQuery(query.value());
                                jpql++;
                            }
                        } else {
                            new PartTree(method.getName(), entity);
                            derived++;
                        }
                    }
                }
            }
            assertEquals(0, NoConnections.attempts.get());
            assertTrue(jpql > 0);
            assertTrue(derived > 0);
            System.out.println(
                    "MIGRATION_MAPPING_CHECK entities="
                            + ENTITIES.length
                            + " JPQL="
                            + jpql
                            + " native="
                            + nativeSql
                            + " derived="
                            + derived
                            + " JDBC attempts=0");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.ada.approval",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = ApprovalWorkflowApplication.class),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern =
                                "com\\.ada\\.approval\\.(migration|api\\.RestApiTest|config\\.security\\.BackendSecurityTest).*")
            })
    static class Components {
        @Bean
        static BeanFactoryPostProcessor persistenceStubs() {
            return factory -> {
                try {
                    for (String name : ENTITIES)
                        factory.registerSingleton(
                                "stub" + name,
                                mock(
                                        Class.forName(
                                                "com.ada.approval.repository."
                                                        + name
                                                        + "Repository")));
                    for (Class<?> type :
                            new Class<?>[] {
                                org.activiti.engine.RepositoryService.class,
                                org.activiti.engine.RuntimeService.class,
                                org.activiti.engine.TaskService.class,
                                org.activiti.engine.HistoryService.class
                            }) factory.registerSingleton("stub" + type.getSimpleName(), mock(type));
                } catch (ClassNotFoundException exception) {
                    throw new IllegalStateException(exception);
                }
            };
        }
    }

    @Test
    void applicationComponentsSecurityAndOpenApiCreateWithoutDatasource() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
                .withUserConfiguration(Components.class)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals(
                                    0, context.getBeansOfType(javax.sql.DataSource.class).size());
                            assertEquals(
                                    0,
                                    context.getBeansOfType(org.activiti.engine.ProcessEngine.class)
                                            .size());
                            assertNotNull(
                                    context.getBean(
                                            org.springframework.security.web.SecurityFilterChain
                                                    .class));
                            assertNotNull(context.getBean(io.swagger.v3.oas.models.OpenAPI.class));
                            assertFalse(context.containsBean("secuiryTagConfig"));
                            assertFalse(context.containsBean("mainController"));
                            assertNotNull(
                                    context.getBean(
                                            com.ada.approval.api.error.GlobalApiExceptionHandler
                                                    .class));
                            assertNotNull(
                                    context.getBean(
                                            com.ada.approval.api.controller
                                                    .LeaveRequestApiController.class));
                            assertTrue(context.getBeanDefinitionNames().length > 0);
                            assertTrue(
                                    context.getBeansOfType(Object.class).values().stream()
                                            .noneMatch(
                                                    bean ->
                                                            bean.getClass()
                                                                    .getName()
                                                                    .startsWith(
                                                                            "com.ada.approval.mapper.")));
                            assertFalse(context.containsBean("myMetaObjectHandler"));
                        });
    }
}
