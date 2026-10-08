package com.ada.approval.repository;

import com.ada.approval.entity.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Queries preserve the existing scalar foreign keys and legacy filters. */
public interface AccountRoleRepository extends JpaRepository<AccountRole, Integer> {
    org.springframework.data.domain.Page<AccountRole> findByRoleId(
            Integer roleId, org.springframework.data.domain.Pageable pageable);

    AccountRole findByAccountIdAndRoleId(Integer accountId, Integer roleId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from AccountRole ar where ar.id = :id")
    int deleteAssignment(@Param("id") Integer id);

    @Query(
            "select ar.accountId, a.userName, e.empNum, e.empName from AccountRole ar "
                    + "left join Role r on r.id = ar.roleId left join Account a on a.id = ar.accountId "
                    + "left join Employee e on e.id = a.empId where ar.roleId = :roleId "
                    + "and (:name is null or e.empName like concat('%', :name, '%')) "
                    + "and (:number is null or e.empNum = :number)")
    List<Object[]> findAssignmentRows(
            @Param("roleId") Integer roleId,
            @Param("name") String name,
            @Param("number") String number);
}
