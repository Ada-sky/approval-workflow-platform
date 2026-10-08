package com.ada.approval.persistence;

import com.ada.approval.entity.*;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Calls JPA lifecycle callbacks directly: no Spring context, engine or JDBC. */
class EntityTimestampListenerTest {
    private final EntityTimestampListener listener = new EntityTimestampListener();
    private static final LocalDateTime EXISTING = LocalDateTime.of(2001, 2, 3, 4, 5);

    static Stream<TimestampedEntity> timestampedEntities() {
        return Stream.of(
                new Account(),
                new Dept(),
                new Employee(),
                new EmployeeStatus(),
                new HolidayApply(),
                new HolidayApproval(),
                new Menu(),
                new Role(),
                new TitleCategory());
    }

    private void assertCurrent(LocalDateTime value, LocalDateTime before, LocalDateTime after) {
        assertNotNull(value);
        assertFalse(value.isBefore(before));
        assertFalse(value.isAfter(after));
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void insertFillsBothNullTimestampsWithTheSameInstant(TimestampedEntity entity) {
        LocalDateTime before = LocalDateTime.now();
        listener.beforeInsert(entity);
        assertCurrent(entity.getCreateTime(), before, LocalDateTime.now());
        assertEquals(entity.getCreateTime(), entity.getUpdateTime());
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void insertPreservesBothExplicitTimestamps(TimestampedEntity entity) {
        entity.setCreateTime(EXISTING);
        entity.setUpdateTime(EXISTING.plusDays(1));
        listener.beforeInsert(entity);
        assertEquals(EXISTING, entity.getCreateTime());
        assertEquals(EXISTING.plusDays(1), entity.getUpdateTime());
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void insertFillsOnlyMissingCreateTime(TimestampedEntity entity) {
        entity.setUpdateTime(EXISTING);
        LocalDateTime before = LocalDateTime.now();
        listener.beforeInsert(entity);
        assertCurrent(entity.getCreateTime(), before, LocalDateTime.now());
        assertEquals(EXISTING, entity.getUpdateTime());
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void insertFillsOnlyMissingUpdateTime(TimestampedEntity entity) {
        entity.setCreateTime(EXISTING);
        LocalDateTime before = LocalDateTime.now();
        listener.beforeInsert(entity);
        assertCurrent(entity.getUpdateTime(), before, LocalDateTime.now());
        assertEquals(EXISTING, entity.getCreateTime());
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void updateRefreshesExistingUpdateTimeAndPreservesCreateTime(TimestampedEntity entity) {
        entity.setCreateTime(EXISTING);
        entity.setUpdateTime(EXISTING);
        LocalDateTime before = LocalDateTime.now();
        listener.beforeUpdate(entity);
        assertCurrent(entity.getUpdateTime(), before, LocalDateTime.now());
        assertEquals(EXISTING, entity.getCreateTime());
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void updateFillsNullUpdateTimeWithoutFillingNullCreateTime(TimestampedEntity entity) {
        LocalDateTime before = LocalDateTime.now();
        listener.beforeUpdate(entity);
        assertCurrent(entity.getUpdateTime(), before, LocalDateTime.now());
        assertNull(entity.getCreateTime());
    }

    @Test
    void callbacksAreRegisteredOnExactlyTheExistingTimestampedEntities() throws Exception {
        assertTrue(
                EntityTimestampListener.class
                        .getMethod("beforeInsert", Object.class)
                        .isAnnotationPresent(PrePersist.class));
        assertTrue(
                EntityTimestampListener.class
                        .getMethod("beforeUpdate", Object.class)
                        .isAnnotationPresent(PreUpdate.class));
        timestampedEntities()
                .forEach(
                        entity ->
                                assertTrue(
                                        Arrays.asList(
                                                        entity.getClass()
                                                                .getAnnotation(
                                                                        EntityListeners.class)
                                                                .value())
                                                .contains(EntityTimestampListener.class)));
        for (Object entity :
                new Object[] {new AccountRole(), new Permission(), new HolidayType()}) {
            assertFalse(entity instanceof TimestampedEntity);
            assertDoesNotThrow(() -> listener.beforeInsert(entity));
            assertDoesNotThrow(() -> listener.beforeUpdate(entity));
        }
    }

    @Test
    void activitiRetainsItsInternalMyBatisClassesAndMappings() throws Exception {
        ClassLoader loader = org.activiti.engine.impl.util.ReflectUtil.class.getClassLoader();
        assertNotNull(Class.forName("org.apache.ibatis.session.SqlSessionFactory", false, loader));
        assertNotNull(loader.getResource("org/activiti/db/mapping/entity/Task.xml"));
        assertNull(loader.getResource("mapper/AccountMapper.xml"));
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("com.ada.approval.mapper.AccountMapper", false, loader));
        assertThrows(
                ClassNotFoundException.class,
                () ->
                        Class.forName(
                                "com.baomidou.mybatisplus.annotation.TableField", false, loader));
    }
}
