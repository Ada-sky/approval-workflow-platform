package com.ada.approval.service;

import com.ada.approval.entity.Employee;
import com.ada.approval.query.EmployeeQuery;

import java.util.List;
import java.util.Map;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-19
 */
public interface IEmployeeService extends CrudService<Employee> {
    /**
     * List employees
     *
     * @param employeeQuery
     * @return
     */
    public Map<String, Object> employeeList(EmployeeQuery employeeQuery);

    /**
     * Create employee
     *
     * @param employee
     */
    public void saveEmployee(Employee employee);

    void saveEmployee(Employee employee, String initialPassword);

    /**
     * Update employee
     *
     * @param employee
     */
    void updateEmployee(Employee employee);

    /**
     * Delete employee record
     *
     * @param id Employee id
     */
    void deleteEmployee(Integer id);

    /**
     * Find the employee's Department Manager
     *
     * @param empId
     * @return
     */
    Employee queryDeptManagerByUserName(Integer empId);

    /**
     * Find the General Manager
     *
     * @return
     */
    Employee findBoss();

    /**
     * Find HR approvers
     *
     * @return
     */
    List<Employee> findAllHrs();
}
