package com.ada.approval.repository;

import com.ada.approval.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Provides employee lookups and explicit relationship projections using scalar foreign-key IDs.
 * Organizational approver queries are validated by workflow services before assignment.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
    org.springframework.data.domain.Page<Employee> findByStatusAndEmpNameContainingIgnoreCase(
            Integer status, String name, org.springframework.data.domain.Pageable pageable);

    Employee findByIdAndStatus(Integer id, Integer status);

    Employee findByEmailAndStatus(String email, Integer status);

    @Query("select max(e.empNum) from Employee e")
    String findMaxEmpNum();

    @Query(
            "select e, a.userName, d.deptName, c.titleName, s.name from Employee e "
                    + "left join Account a on a.empId = e.id "
                    + "left join Dept d on d.id = e.deptId "
                    + "left join TitleCategory c on c.id = e.titleCategoryId "
                    + "left join EmployeeStatus s on s.id = e.employStatusId "
                    + "where e.status = 1 and (:name is null or e.empName like concat('%', :name, '%')) "
                    + "and (:deptId is null or e.deptId = :deptId) and (:number is null or e.empNum = :number)")
    List<Object[]> findEmployeeRows(
            @Param("name") String name,
            @Param("deptId") Integer deptId,
            @Param("number") String number);

    @Query(
            "select manager from Employee manager where manager.id = "
                    + "(select d.managerId from Employee e left join Dept d on d.id = e.deptId where e.id = :empId)")
    Employee findDepartmentManager(@Param("empId") Integer empId);

    @Query(
            "select e from Employee e join TitleCategory c on c.id = e.titleCategoryId where c.titleName in :titles")
    Employee findSingleByJobTitles(@Param("titles") java.util.Collection<String> titles);

    @Query(
            "select e from Employee e join TitleCategory c on c.id = e.titleCategoryId where c.titleName in :titles")
    List<Employee> findAllByJobTitles(@Param("titles") java.util.Collection<String> titles);
}
