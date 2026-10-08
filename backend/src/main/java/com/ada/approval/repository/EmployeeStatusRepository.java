package com.ada.approval.repository;

import com.ada.approval.entity.EmployeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Repository for the existing table; foreign keys remain scalar IDs. */
public interface EmployeeStatusRepository extends JpaRepository<EmployeeStatus, Integer> {
    org.springframework.data.domain.Page<EmployeeStatus> findByStatusAndNameContainingIgnoreCase(
            Integer status, String name, org.springframework.data.domain.Pageable pageable);

    List<EmployeeStatus> findAllByNameIgnoreCaseAndStatus(String name, Integer status);

    EmployeeStatus findByNameAndStatus(String name, Integer status);

    @Query(
            "select s from EmployeeStatus s where s.status = 1 and s.name like concat('%', :name, '%')")
    List<EmployeeStatus> searchActive(@Param("name") String name);
}
