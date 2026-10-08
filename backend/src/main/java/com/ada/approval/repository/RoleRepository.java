package com.ada.approval.repository;

import com.ada.approval.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Repository for the existing table; foreign keys remain scalar IDs. */
public interface RoleRepository extends JpaRepository<Role, Integer> {
    org.springframework.data.domain.Page<Role> findByStatusAndRoleNameContainingIgnoreCase(
            Integer status, String name, org.springframework.data.domain.Pageable pageable);

    Role findByRoleNameAndStatus(String roleName, Integer status);

    @Query(
            "select r from Role r where (:name is null or r.roleName = :name) and (:status is null or r.status = :status)")
    List<Role> search(@Param("name") String name, @Param("status") Integer status);
}
