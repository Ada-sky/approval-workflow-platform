package com.ada.approval.service.impl;

import com.ada.approval.service.CrudService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Retains non-null partial-update semantics for existing business-service callers using JPA. Null
 * fields leave mapped columns unchanged; identifiers are never copied during an update.
 */
public abstract class JpaCrudService<T> implements CrudService<T> {
    protected final JpaRepository<T, Integer> repository;

    protected JpaCrudService(JpaRepository<T, Integer> repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public T getById(Integer id) {
        return id == null ? null : repository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public boolean save(T entity) {
        // Flush before callers use generated IDs and before the enclosing transaction completes.
        repository.saveAndFlush(entity);
        return true;
    }

    @Override
    @Transactional
    public boolean updateById(T entity) {
        Field idField = ReflectionUtils.findField(entity.getClass(), "id");
        if (idField == null) throw new IllegalArgumentException("Entity has no id field");
        ReflectionUtils.makeAccessible(idField);
        Integer id = (Integer) ReflectionUtils.getField(idField, entity);
        T existing = getById(id);
        if (existing == null) return false;
        ReflectionUtils.doWithFields(
                entity.getClass(),
                field -> {
                    if (field.isAnnotationPresent(Column.class)
                            && !field.isAnnotationPresent(Id.class)) {
                        ReflectionUtils.makeAccessible(field);
                        Object value = ReflectionUtils.getField(field, entity);
                        if (value != null) ReflectionUtils.setField(field, existing, value);
                    }
                });
        repository.saveAndFlush(existing);
        return true;
    }

    @Override
    @Transactional
    public boolean saveBatch(Collection<T> entities) {
        repository.saveAll(entities);
        repository.flush();
        return true;
    }
}
