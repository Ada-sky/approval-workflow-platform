package com.ada.approval.service;

import com.ada.approval.entity.EmployeeStatus;

import java.util.List;
import java.util.Map;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-18
 */
public interface IEmployeeStatusService extends CrudService<EmployeeStatus> {
    /** List job titles */
    Map<String, Object> findEmployeeStatusList(String name);

    /**
     * Create employee status
     *
     * @param employeeStatus
     */
    void saveEmployeeStatus(EmployeeStatus employeeStatus);

    /**
     * Update employee status
     *
     * @param employeeStatus Employee status
     */
    void updateEmployeeStatus(EmployeeStatus employeeStatus);

    /**
     * Delete employee status
     *
     * @param ids
     */
    void deleteEmployeeStatus(Integer[] ids);

    /**
     * List employee statuses
     *
     * @return
     */
    List<EmployeeStatus> queryAllEmployeeStatus();
}
