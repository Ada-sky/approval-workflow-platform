package com.ada.approval.repository;

import com.ada.approval.entity.HolidayApproval;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the existing table; foreign keys remain scalar IDs. */
public interface HolidayApprovalRepository extends JpaRepository<HolidayApproval, Integer> {
    @org.springframework.data.jpa.repository.Query(
            "select a from HolidayApproval a where a.userId = :accountId "
                    + "and a.result in (1, 2, 3, 4, 5, 6) "
                    + "and exists (select h.id from HolidayApply h where h.processInstanceId = a.processInstanceId) "
                    + "order by a.createTime desc, a.id desc")
    org.springframework.data.domain.Page<HolidayApproval> findDecisionHistory(
            @org.springframework.data.repository.query.Param("accountId") Integer accountId,
            org.springframework.data.domain.Pageable pageable);

    boolean existsByProcessInstanceIdAndUserIdAndResultIn(
            String processInstanceId, Integer userId, java.util.Collection<Integer> results);

    java.util.List<HolidayApproval> findByProcessInstanceIdOrderByCreateTimeAscIdAsc(
            String processInstanceId);

    HolidayApproval findFirstByProcessInstanceIdAndTaskDefKeyOrderByCreateTimeDescIdDesc(
            String processInstanceId, String taskDefKey);
}
