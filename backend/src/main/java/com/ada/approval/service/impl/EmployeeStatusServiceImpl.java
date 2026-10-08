package com.ada.approval.service.impl;

import com.ada.approval.entity.EmployeeStatus;
import com.ada.approval.service.IEmployeeStatusService;
import com.ada.approval.repository.EmployeeStatusRepository;
import com.ada.approval.utils.AssertUtil;
import com.ada.approval.utils.PageResultUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service implementation
 *
 * @author Yan Min
 * @since 2023-12-18
 */
@Service
public class EmployeeStatusServiceImpl extends JpaCrudService<EmployeeStatus>
        implements IEmployeeStatusService {
    private final EmployeeStatusRepository employeeStatusRepository;

    public EmployeeStatusServiceImpl(EmployeeStatusRepository repository) {
        super(repository);
        this.employeeStatusRepository = repository;
    }

    @Override
    public Map<String, Object> findEmployeeStatusList(String name) {
        List<EmployeeStatus> list = employeeStatusRepository.searchActive(name);
        return PageResultUtil.getResult((long) list.size(), list);
    }

    @Override
    public void saveEmployeeStatus(EmployeeStatus employeeStatus) {
        // Validate required fields
        AssertUtil.isTrue(
                StringUtils.isBlank(employeeStatus.getName()), "Employee status name is required");
        // Find the employee status by name
        EmployeeStatus temp =
                employeeStatusRepository.findByNameAndStatus(employeeStatus.getName(), 1);
        // Validate
        AssertUtil.isTrue(null != temp, "Employee status already exists");
        // Create the record
        employeeStatus.setStatus(1);
        boolean save = this.save(employeeStatus);
        AssertUtil.isTrue(!save, "Unable to create employee status");
    }

    @Override
    public void updateEmployeeStatus(EmployeeStatus employeeStatus) {
        // Validate required fields
        AssertUtil.isTrue(
                StringUtils.isBlank(employeeStatus.getName()), "Employee status name is required");
        // Find the employee status by name
        EmployeeStatus temp =
                employeeStatusRepository.findByNameAndStatus(employeeStatus.getName(), 1);
        AssertUtil.isTrue(
                null != temp && !(temp.getId().equals(employeeStatus.getId())),
                "Employee status name already exists");
        // Update the record
        boolean b = this.updateById(employeeStatus);
        AssertUtil.isTrue(!b, "Unable to update employee status");
    }

    @Override
    public void deleteEmployeeStatus(Integer[] ids) {
        for (Integer id : ids) {
            // Find by ID
            EmployeeStatus employeeStatus = this.getById(id);
            // Verify the record exists before deletion
            AssertUtil.isTrue(null == employeeStatus, "Employee status not found");
            // Update status
            employeeStatus.setStatus(0); // 0 indicates soft deletion
            // Update the soft-delete marker
            boolean b = this.updateById(employeeStatus);
            AssertUtil.isTrue(!b, "Unable to delete employee status");
        }
    }

    @Override
    public List<EmployeeStatus> queryAllEmployeeStatus() {
        List<EmployeeStatus> list = repository.findAll();
        return list;
    }
}
