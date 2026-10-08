package com.ada.approval.service;

import java.util.Collection;

/** Small persistence-neutral contract for services migrated to JPA. */
public interface CrudService<T> {
    T getById(Integer id);

    boolean save(T entity);

    boolean updateById(T entity);

    boolean saveBatch(Collection<T> entities);
}
