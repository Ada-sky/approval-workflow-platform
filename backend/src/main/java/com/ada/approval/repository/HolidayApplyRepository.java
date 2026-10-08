package com.ada.approval.repository;

import com.ada.approval.entity.HolidayApply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Queries Flyway-owned leave-request tables through JPA. Scalar account/process identifiers do not
 * map Activiti internal tables as application entities.
 */
public interface HolidayApplyRepository extends JpaRepository<HolidayApply, Integer> {
    // Approval and withdrawal lock the same row to serialize competing workflow transitions.
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HolidayApply h where h.id = :id")
    HolidayApply findLockedById(@Param("id") Integer id);

    org.springframework.data.domain.Page<HolidayApply> findByAccountIdAndIsValid(
            Integer accountId, Integer valid, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<HolidayApply>
            findByAccountIdAndIsValidAndApplicantArchivedFalse(
                    Integer accountId,
                    Integer valid,
                    org.springframework.data.domain.Pageable pageable);

    boolean existsByHolidayType(Integer holidayType);

    HolidayApply findByProcessInstanceId(String processInstanceId);

    // Keep the old MySQL string/date/status comparisons instead of introducing new parsing rules.
    @Query(
            value =
                    "select h.* from t_holiday_apply h where h.account_id = :accountId and h.is_valid = 1 "
                            + "and (:title is null or h.title like concat('%', :title, '%')) "
                            + "and (:startTime is null or h.start_time >= :startTime) "
                            + "and (:endTime is null or h.end_time <= :endTime) "
                            + "and (:status is null or h.status = :status)",
            nativeQuery = true)
    List<HolidayApply> findMyApplications(
            @Param("accountId") Integer accountId,
            @Param("title") String title,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("status") String status);

    List<HolidayApply> findByProcessInstanceIdIn(java.util.Collection<String> processInstanceIds);

    // Activiti exposes the existing numeric application business key as a String.
    @Query(
            value = "select h.* from t_holiday_apply h where h.id = :businessKey",
            nativeQuery = true)
    HolidayApply findByBusinessKey(@Param("businessKey") String businessKey);
}
