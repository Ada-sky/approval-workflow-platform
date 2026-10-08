package com.ada.approval.service;

import com.ada.approval.entity.HolidayApply;
import com.ada.approval.query.HolidayApplyQuery;
import com.ada.approval.query.MyTaskQuery;

import java.util.Map;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-25
 */
public interface IHolidayApplyService extends CrudService<HolidayApply> {
    void validateTask(Integer holidayApplyId, String taskId);

    HolidayApply findOwnApplication(Integer id);

    /**
     * Create the leave request
     *
     * @param holidayApply
     */
    public void saveHolidayApply(HolidayApply holidayApply);

    /**
     * List leave requests
     *
     * @param holidayApplyQuery
     * @return
     */
    Map<String, Object> queryMyHolidayApply(HolidayApplyQuery holidayApplyQuery);

    /**
     * Delete leave request
     *
     * @param id
     */
    void deleteHolidayApply(Integer id);

    /**
     * List pending tasks for the current user
     *
     * @param myTaskQuery
     * @return
     */
    Map<String, Object> queryMyTaskList(MyTaskQuery myTaskQuery);

    /**
     * Leave approval
     *
     * @param holidayApplyId
     * @param taskId
     * @param stepPass
     * @param remark
     */
    void processTask(Integer holidayApplyId, String taskId, boolean stepPass, String remark);

    /**
     * Count pending tasks for the current user
     *
     * @return
     */
    int countMyTask();
}
