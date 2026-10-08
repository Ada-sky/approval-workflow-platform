package com.ada.approval.service.impl;

import com.ada.approval.repository.EmployeeRepository;
import com.ada.approval.repository.AccountRepository;
import java.util.Objects;
import org.springframework.beans.BeanUtils;
import java.util.ArrayList;

import com.ada.approval.entity.Account;
import com.ada.approval.entity.Employee;
import com.ada.approval.query.EmployeeQuery;
import com.ada.approval.service.IAccountService;
import com.ada.approval.service.IEmployeeService;
import com.ada.approval.utils.AssertUtil;
import com.ada.approval.utils.PageResultUtil;
import com.ada.approval.service.projection.EmployeeRow;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Coordinates employee records and their linked login accounts. Account lookup uses emp_id, not
 * email; deletion preserves records through the existing soft- delete flags.
 */
@Service
public class EmployeeServiceImpl extends JpaCrudService<Employee> implements IEmployeeService {
    private final EmployeeRepository employeeRepository;

    private final AccountRepository accountRepository;

    public EmployeeServiceImpl(EmployeeRepository repository, AccountRepository accountRepository) {
        super(repository);
        this.employeeRepository = repository;
        this.accountRepository = accountRepository;
    }

    @Autowired private IAccountService accountService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> employeeList(EmployeeQuery employeeQuery) {
        List<EmployeeRow> records = new ArrayList<>();
        String name =
                StringUtils.isEmpty(employeeQuery.getEmpName()) ? null : employeeQuery.getEmpName();
        String number =
                StringUtils.isEmpty(employeeQuery.getEmpNum()) ? null : employeeQuery.getEmpNum();
        for (Object[] row :
                employeeRepository.findEmployeeRows(name, employeeQuery.getDeptId(), number)) {
            EmployeeRow vo = new EmployeeRow();
            BeanUtils.copyProperties((Employee) row[0], vo);
            vo.setUserName((String) row[1]);
            vo.setDeptName((String) row[2]);
            vo.setTitleName((String) row[3]);
            vo.setEmpStatus((String) row[4]);
            records.add(vo);
        }
        // Legacy custom mapper had no pagination interceptor: all rows, total left at zero.
        return PageResultUtil.getResult(0L, records);
    }

    @Transactional
    @Override
    public void saveEmployee(Employee employee) {
        saveEmployee(employee, null);
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('100211')")
    public void saveEmployee(Employee employee, String initialPassword) {
        AssertUtil.isTrue(employee.getId() != null, "A new employee must not specify an ID");
        com.ada.approval.config.security.PasswordPolicy.validate(initialPassword);
        // 1.Validate required profile fields
        checkParam(employee.getEmpName(), employee.getMobile(), employee.getEmail());
        // 2.Validate email uniqueness because email is the login name
        Employee temp = this.findEmployeeByEmail(employee.getEmail());
        AssertUtil.isTrue(null != temp, "Email is already linked to an employee");
        // 3.Save the employee
        // Generate six-digit employee numbers starting at 000001
        String empNum = this.generaterEmpNum();
        employee.setEmpNum(empNum); // Employee number
        employee.setFormalStatus("1"); // Default to confirmed employment
        employee.setStatus(1); // Enabled account
        boolean save1 = this.save(employee);
        AssertUtil.isTrue(!save1, "Unable to create employee");
        // 4.Create the linked employee account
        Account account = new Account();
        account.setEmpId(employee.getId());
        String pwd = passwordEncoder.encode(initialPassword);
        account.setPassword(pwd);
        account.setStatus(1);
        account.setUserName(employee.getEmail()); // Use the employee email as the login name
        boolean save2 = accountService.save(account);
        AssertUtil.isTrue(!save2, "Unable to create account");
    }

    @Override
    @Transactional
    public void updateEmployee(Employee employee) {
        // 1.Find the employee by ID
        Employee temp = employeeRepository.findByIdAndStatus(employee.getId(), 1);
        // Verify the employee exists before update
        AssertUtil.isTrue(null == temp, "Employee not found");
        // 2.Validate required fields
        this.checkParam(employee.getEmpName(), employee.getMobile(), employee.getEmail());
        // 3.Validate email uniqueness
        temp = this.findEmployeeByEmail(employee.getEmail());
        AssertUtil.isTrue(
                null != temp && !(temp.getId().equals(employee.getId())),
                "Email is already registered");
        Account account = accountService.findAccountByEmpId(employee.getId());
        AssertUtil.isTrue(account == null, "Employee has no linked account");
        boolean emailChanged = !Objects.equals(account.getUsername(), employee.getEmail());
        if (emailChanged) {
            AssertUtil.isTrue(
                    accountRepository.existsByUserNameAndIdNot(
                            employee.getEmail(), account.getId()),
                    "Email is already used as a login name");
        }
        boolean updated = this.updateById(employee);
        AssertUtil.isTrue(!updated, "Unable to update employee!");
        if (emailChanged) {
            account.setUserName(employee.getEmail());
            AssertUtil.isTrue(!accountService.updateById(account), "Unable to update account");
        }
    }

    @Transactional
    @Override
    public void deleteEmployee(Integer id) {
        // Find the account by ID
        Employee employee = employeeRepository.findByIdAndStatus(id, 1);
        // Verify the account to delete exists
        AssertUtil.isTrue(null == employee, "Employee not found");
        Account account = accountService.findAccountByEmpId(id);
        AssertUtil.isTrue(account == null, "Employee has no linked account");
        // Soft-delete the existing record
        employee.setStatus(0); // 0 indicates deletion
        // Update the record
        boolean b = this.updateById(employee);
        AssertUtil.isTrue(!b, "Unable to delete employee");

        // ---------Also soft-delete the linked account by setting status to 0-----------------
        account.setStatus(0); // Disable login
        // Update the record
        boolean flag = accountService.updateById(account);
        AssertUtil.isTrue(!flag, "Unable to update account");
    }

    @Override
    public Employee queryDeptManagerByUserName(Integer empId) {
        // 1.Find the employee by ID
        Employee employee = this.getById(empId);
        AssertUtil.isTrue(null == employee, "Employee not found");
        // 2.Find the employee's Department Manager
        return employeeRepository.findDepartmentManager(empId);
    }

    @Override
    public Employee findBoss() {
        return employeeRepository.findSingleByJobTitles(
                com.ada.approval.workflow.WorkflowTitleNames.generalManagerNames());
    }

    @Override
    public List<Employee> findAllHrs() {
        return employeeRepository.findAllByJobTitles(
                com.ada.approval.workflow.WorkflowTitleNames.hrNames());
    }

    /**
     * Generate the employee number
     *
     * @return
     */
    private String generaterEmpNum() {
        String empNum = "000001";
        // Increment the highest existing employee number
        // Start at 000001 when no employees exist
        // Look up the existing record first
        String maxEmpNum = employeeRepository.findMaxEmpNum();
        // Preserve the old all-null aggregate failure; empty-database handling needs a separate
        // fix.
        java.util.Objects.requireNonNull(maxEmpNum, "Legacy employee-number aggregate is null");
        if (StringUtils.isNotEmpty(maxEmpNum)) { // Existing record found
            // Convert to integer
            Integer code = Integer.valueOf(maxEmpNum) + 1;
            empNum = code.toString();
            int length = empNum.length();
            for (int i = 6; i > length; i--) {
                empNum = "0" + empNum;
            }
        }
        return empNum;
    }

    /**
     * Find the employee by email
     *
     * @param email
     * @return
     */
    private Employee findEmployeeByEmail(String email) {
        Employee employee = employeeRepository.findByEmailAndStatus(email, 1);
        return employee;
    }

    /**
     * Validate required fields
     *
     * @param empName
     * @param mobile
     * @param email
     */
    private void checkParam(String empName, String mobile, String email) {
        AssertUtil.isTrue(StringUtils.isBlank(empName), "Employee name is required");
        AssertUtil.isTrue(StringUtils.isBlank(mobile), "Mobile number is required");
        AssertUtil.isTrue(StringUtils.isBlank(email), "Email is required");
    }
}
