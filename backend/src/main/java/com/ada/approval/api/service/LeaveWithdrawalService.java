package com.ada.approval.api.service;

import com.ada.approval.api.dto.ApiMapper;
import com.ada.approval.api.error.ApiException;
import com.ada.approval.enums.*;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.IAccountService;
import org.activiti.engine.*;
import org.activiti.engine.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Coordinates applicant-only withdrawal and its application audit record. A request-row lock and
 * shared JPA/Activiti transaction prevent approval and withdrawal from committing conflicting
 * states.
 */
@Service
public class LeaveWithdrawalService {
    private final HolidayApplyRepository requests;
    private final HolidayApprovalRepository audits;
    private final RuntimeService runtime;
    private final TaskService tasks;
    private final IAccountService accounts;

    public LeaveWithdrawalService(
            HolidayApplyRepository requests,
            HolidayApprovalRepository audits,
            RuntimeService runtime,
            TaskService tasks,
            IAccountService accounts) {
        this.requests = requests;
        this.audits = audits;
        this.runtime = runtime;
        this.tasks = tasks;
        this.accounts = accounts;
    }

    private boolean eligible(HolidayApply request, Account account) {
        return account != null
                && account.isEnabled()
                && Objects.equals(request.getAccountId(), account.getId())
                && Objects.equals(request.getIsValid(), 1)
                && Set.of(1, 2).contains(Objects.requireNonNullElse(request.getStatus(), 0))
                && request.getProcessInstanceId() != null
                && !request.getProcessInstanceId().isBlank();
    }

    private Task activeTask(HolidayApply request) {
        String process = request.getProcessInstanceId();
        if (runtime.createProcessInstanceQuery().processInstanceId(process).active().singleResult()
                == null) return null;
        List<Task> active = tasks.createTaskQuery().processInstanceId(process).active().list();
        if (active.size() != 1
                || "UNKNOWN".equals(ApiMapper.stage(active.get(0).getTaskDefinitionKey())))
            return null;
        Task task = active.get(0);
        return Objects.equals(process, task.getProcessInstanceId()) ? task : null;
    }

    /**
     * Provides a display capability, not a substitute for the checks repeated during withdrawal.
     */
    public boolean canWithdraw(HolidayApply request, Account current) {
        return eligible(request, current) && activeTask(request) != null;
    }

    /**
     * Locks and revalidates ownership and eligibility before recording withdrawal. Runtime
     * execution is deleted while application audit and engine history remain preserved.
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Integer id) {
        HolidayApply request = requests.findLockedById(id);
        if (request == null || !Objects.equals(request.getIsValid(), 1))
            throw new ApiException(404, "Leave request not found");
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Account current = accounts.findAccountByUserName(username);
        if (current == null
                || !current.isEnabled()
                || !Objects.equals(request.getAccountId(), current.getId()))
            throw new AccessDeniedException("Only the applicant may withdraw this request");
        if (!eligible(request, current))
            throw new ApiException(409, "This request cannot be withdrawn");
        Task task = activeTask(request);
        if (task == null)
            throw new ApiException(
                    409, "No consistent active approval workflow is available for withdrawal");
        HolidayApproval audit = new HolidayApproval();
        audit.setProcessInstanceId(request.getProcessInstanceId());
        audit.setTaskId(task.getId());
        audit.setTaskDefKey(task.getTaskDefinitionKey());
        audit.setResult(HolidayApprovalStatus.WITHDRAWN.getType());
        audit.setUserId(current.getId());
        audit.setUserName(username);
        audit.setCreateTime(LocalDateTime.now());
        audit.setRemark("Request withdrawn by applicant");
        audits.saveAndFlush(audit);
        request.setStatus(HolidayApplyStatus.WITHDRAWN.getType());
        request.setApprovalStatus(HolidayApprovalStatus.WITHDRAWN.getType());
        requests.saveAndFlush(request);
        runtime.deleteProcessInstance(
                request.getProcessInstanceId(), "Leave withdrawn by applicant");
    }
}
