package com.ada.approval.service;

import com.ada.approval.entity.Dept;

import java.util.Map;
import java.util.Objects;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-15
 */
public interface IDeptService extends CrudService<Dept> {
    /**
     * List departments
     *
     * @return Department list
     */
    public Map<String, Object> deptList();

    /**
     * Create department
     *
     * @param dept
     */
    public void saveDept(Dept dept);

    /**
     * Update department
     *
     * @param dept
     */
    void upateDept(Dept dept);

    /**
     * Delete department
     *
     * @param id Department number
     */
    void deleteDept(Integer id);
}
