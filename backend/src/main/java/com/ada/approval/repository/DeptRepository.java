package com.ada.approval.repository;

import com.ada.approval.entity.Dept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Queries preserve the existing scalar foreign keys and legacy filters. */
public interface DeptRepository extends JpaRepository<Dept, Integer> {
    boolean existsByManagerIdAndStatus(Integer managerId, Integer status);

    org.springframework.data.domain.Page<Dept> findByStatusAndDeptNameContainingIgnoreCase(
            Integer status, String name, org.springframework.data.domain.Pageable pageable);

    Dept findByDeptNumAndStatus(String deptNum, Integer status);

    long countByParentIdAndStatus(Integer parentId, Integer status);

    @Query("select d from Dept d where d.deptName = :name and d.level = :level and d.status = 1")
    Dept findActiveByNameAndLevel(@Param("name") String name, @Param("level") Integer level);

    @Query(
            "select d, e.empName, coalesce(parent.deptName, '-') from Dept d "
                    + "left join Employee e on e.id = d.managerId left join Dept parent on parent.id = d.parentId "
                    + "where d.status = 1")
    List<Object[]> findDepartmentRows();
}
