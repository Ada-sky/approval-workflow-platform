package com.ada.approval.repository;

import com.ada.approval.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;

/**
 * Evaluates the existing numeric grant model across account, role, menu and permission tables.
 * Enabled/valid filters prevent inactive records from contributing authorities or approval-function
 * access.
 */
public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    long countByRoleId(Integer roleId);

    @Modifying
    @Query("delete from Permission p where p.roleId = :roleId")
    int deleteForRole(@Param("roleId") Integer roleId);

    @Modifying
    @Query("delete from Permission p where p.menuId = :menuId")
    int deleteForMenu(@Param("menuId") Integer menuId);

    @Query("select p.menuId from Permission p where p.roleId = :roleId")
    List<Integer> findMenuIds(@Param("roleId") Integer roleId);

    @Query(
            "select distinct m.optValue from Permission p, AccountRole ar, Account a, Role r, Menu m "
                    + "where p.roleId = ar.roleId and ar.accountId = a.id and r.id = ar.roleId and m.id = p.menuId "
                    + "and p.aclValue = m.optValue and a.status = 1 and r.status = 1 and m.isValid = 1 and a.userName = :userName")
    List<String> findAuthorities(@Param("userName") String userName);

    @Query(
            "select distinct m.url from Permission p, AccountRole ar, Account a, Role r, Menu m "
                    + "where p.roleId = ar.roleId and ar.accountId = a.id and r.id = ar.roleId and m.id = p.menuId "
                    + "and p.aclValue = m.optValue and a.status = 1 and r.status = 1 and m.isValid = 1 and a.userName = :userName")
    List<String> findGrantedMenuUrls(@Param("userName") String userName);
}
