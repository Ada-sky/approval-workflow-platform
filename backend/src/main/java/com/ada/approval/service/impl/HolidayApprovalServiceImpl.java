package com.ada.approval.service.impl;

import com.ada.approval.entity.HolidayApproval;
import com.ada.approval.repository.HolidayApprovalRepository;
import com.ada.approval.service.IHolidayApprovalService;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;
import java.util.List;

/**
 * Reads and persists application approval records through JPA. These records supplement Activiti
 * history rather than owning engine task state.
 */
@Service
public class HolidayApprovalServiceImpl extends JpaCrudService<HolidayApproval>
        implements IHolidayApprovalService {
    private final HolidayApprovalRepository approvals;

    public HolidayApprovalServiceImpl(HolidayApprovalRepository repository) {
        super(repository);
        this.approvals = repository;
    }

    @Override
    public HolidayApproval findLatestApproval(String processInstanceId, String taskDefKey) {
        if (StringUtils.isBlank(processInstanceId) || StringUtils.isBlank(taskDefKey)) return null;
        return approvals.findFirstByProcessInstanceIdAndTaskDefKeyOrderByCreateTimeDescIdDesc(
                processInstanceId, taskDefKey);
    }

    @Override
    public HolidayApproval queryHolidayApprovalByprocIdAndUserNameAndTaskDefKey(
            String processInstanceId, String taskDefKey, List<String> users) {
        // Compatibility entry point: historical actors come from the audit, not current staff
        // assignments.
        return findLatestApproval(processInstanceId, taskDefKey);
    }
}
