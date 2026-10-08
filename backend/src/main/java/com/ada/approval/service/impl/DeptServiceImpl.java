package com.ada.approval.service.impl;

import com.ada.approval.repository.DeptRepository;
import org.springframework.beans.BeanUtils;
import java.util.ArrayList;

import com.ada.approval.entity.Dept;
import com.ada.approval.service.IDeptService;
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
 * @since 2023-12-15
 */
@Service
public class DeptServiceImpl extends JpaCrudService<Dept> implements IDeptService {
    private final DeptRepository deptRepository;

    public DeptServiceImpl(DeptRepository repository) {
        super(repository);
        this.deptRepository = repository;
    }

    @Override
    public Map<String, Object> deptList() {
        List<Dept> depts = new ArrayList<>();
        for (Object[] row : deptRepository.findDepartmentRows()) {
            Dept result = new Dept();
            // These columns were absent from the original list SELECT; keep the response shape.
            BeanUtils.copyProperties((Dept) row[0], result, "managerId", "status");
            result.setManager((String) row[1]);
            result.setParentDeptName((String) row[2]);
            depts.add(result);
        }
        Map<String, Object> map = PageResultUtil.getResult((long) depts.size(), depts);
        return map;
    }

    @Override
    public void saveDept(Dept dept) {
        // 1.Validate parameters
        // 1-0 Validate required parameters
        checkParams(dept.getDeptName(), dept.getDeptNum());
        // 1-1 Department names must be unique at the same level
        Dept temp = findDeptByDeptNameAndLevel(dept.getDeptName(), dept.getLevel());
        AssertUtil.isTrue(
                null != temp && (dept.getParentId().equals(temp.getParentId())),
                "Department name already exists at this level");
        // 1-2 Department numbers must be unique
        // Find the department by number
        Dept d = findDeptByDeptNum(dept.getDeptNum());
        AssertUtil.isTrue(d != null, "Department number already exists!");

        dept.setStatus(1); // Enabled by default
        //        dept.setCreateTime(LocalDateTime.now());
        //        dept.setUpdateTime(LocalDateTime.now());
        // Create the record
        boolean save = this.save(dept); // True indicates successful creation
        AssertUtil.isTrue(!save, "Unable to create department");
    }

    @Override
    public void upateDept(Dept dept) {
        // 1.Verify the department exists before update
        Dept temp = this.getById(dept.getId()); // Find the department by ID
        AssertUtil.isTrue(null == temp, "Department not found!");
        // Validate required fields
        checkParams(dept.getDeptName(), dept.getDeptNum());
        // 2.Validate department number
        temp = this.findDeptByDeptNum(dept.getDeptNum());
        AssertUtil.isTrue(
                null != temp && !(temp.getId().equals(dept.getId())),
                "Department number already exists");
        // 3.Validate department name
        // Department names must be unique among siblings, excluding the current record
        temp = this.findDeptByDeptNameAndLevel(dept.getDeptName(), dept.getLevel());
        AssertUtil.isTrue(
                null != temp
                        && !(temp.getId().equals(dept.getId())
                                && (dept.getParentId().equals(temp.getParentId()))),
                "Department name must be unique at this level");

        // 4.Update
        boolean b = this.updateById(dept);
        AssertUtil.isTrue(!b, "Unable to update department");
    }

    @Override
    public void deleteDept(Integer id) {
        // Find the department by ID
        Dept dept = this.getById(id);
        // Verify the department exists
        AssertUtil.isTrue(null == dept, "Department not found!");
        // Delete child departments before their parent
        //   Check for child departments before deletion
        long count = deptRepository.countByParentIdAndStatus(dept.getId(), 1);
        AssertUtil.isTrue(count > 0, "Remove child departments before deleting this department!");
        // Delete the record
        dept.setStatus(0); // Status 0 marks a soft-deleted record
        boolean b = this.updateById(dept);
        AssertUtil.isTrue(!b, "Unable to delete department");
    }

    /**
     * Find the department by name and level
     *
     * @param deptName
     * @param level
     * @return
     */
    private Dept findDeptByDeptNameAndLevel(String deptName, Integer level) {
        Dept dept = deptRepository.findActiveByNameAndLevel(deptName, level);
        return dept;
    }

    /**
     * Find the department by number
     *
     * @param deptNum
     * @return
     */
    private Dept findDeptByDeptNum(String deptNum) {
        Dept dept = deptRepository.findByDeptNumAndStatus(deptNum, 1);
        return dept;
    }

    /**
     * Validate required fields
     *
     * @param deptName
     * @param deptNum
     */
    private void checkParams(String deptName, String deptNum) {
        AssertUtil.isTrue(StringUtils.isBlank(deptName), "Department name is required");
        AssertUtil.isTrue(StringUtils.isBlank(deptNum), "Department number is required");
    }
}
