package com.ada.approval.service;

import com.ada.approval.entity.HolidayApproval;

import java.util.List;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-25
 */
public interface IHolidayApprovalService extends CrudService<HolidayApproval> {
    HolidayApproval findLatestApproval(String processInstanceId, String taskDefKey);

    /**
     * Find approval progress
     *
     * @param processInstanceId
     * @param taskDefKey
     * @param asList
     * @return
     */
    HolidayApproval queryHolidayApprovalByprocIdAndUserNameAndTaskDefKey(
            String processInstanceId, String taskDefKey, List<String> list);
}
