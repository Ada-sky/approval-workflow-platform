package com.ada.approval.repository;

import com.ada.approval.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the existing table; foreign keys remain scalar IDs. */
public interface AccountRepository extends JpaRepository<Account, Integer> {
    Account findByUserNameAndStatus(String userName, Integer status);

    Account findByEmpId(Integer empId);

    /** One scalar projection for an entire response collection; includes historical accounts. */
    @org.springframework.data.jpa.repository.Query(
            "select a.id, e.empName from Account a left join Employee e on e.id = a.empId where a.id in :ids")
    java.util.List<Object[]> findApplicantNames(
            @org.springframework.data.repository.query.Param("ids")
                    java.util.Collection<Integer> ids);

    /** Display-only projection; never selects account credentials. */
    @org.springframework.data.jpa.repository.Query(
            "select a.id, e.empName, a.userName from Account a left join Employee e on e.id = a.empId where a.id in :ids")
    java.util.List<Object[]> findAccountDisplayRows(
            @org.springframework.data.repository.query.Param("ids")
                    java.util.Collection<Integer> ids);

    boolean existsByUserNameAndIdNot(String userName, Integer id);
}
