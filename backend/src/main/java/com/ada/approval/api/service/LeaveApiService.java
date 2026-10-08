package com.ada.approval.api.service;

import com.ada.approval.api.dto.*;
import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.error.ApiException;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.activiti.engine.TaskService;
import org.activiti.engine.task.Task;
import com.ada.approval.query.MyTaskQuery;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordinates REST leave operations and DTO enrichment. Ownership or task authorization precedes
 * details and history access; Activiti remains authoritative for execution.
 */
@Service
public class LeaveApiService {
    @Autowired private IHolidayApplyService workflow;
    @Autowired private IAccountService accounts;
    @Autowired private HolidayApplyRepository requests;
    @Autowired private HolidayApprovalRepository approvals;
    @Autowired private HolidayTypeRepository types;
    @Autowired private TaskService tasks;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WorkflowProgressService progress;
    @Autowired private LeaveWithdrawalService withdrawals;
    @Autowired private com.ada.approval.workflow.ApplicantWorkflow applicantWorkflow;

    private Map<Integer, String> applicantNames(Collection<? extends HolidayApply> rows) {
        Set<Integer> ids =
                rows.stream()
                        .map(HolidayApply::getAccountId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Integer, String> names = new HashMap<>();
        if (!ids.isEmpty())
            for (Object[] row : accountRepository.findApplicantNames(ids))
                names.put((Integer) row[0], (String) row[1]);
        return names;
    }

    private LeaveResponse response(HolidayApply row, Map<Integer, String> names) {
        LeaveResponse dto = ApiMapper.leave(row);
        dto.setApplicantName(names.get(row.getAccountId()));
        return dto;
    }

    private LeaveResponse response(HolidayApply row) {
        return response(row, applicantNames(Collections.singletonList(row)));
    }

    private Account current() {
        return ApiPaging.required(
                accounts.findAccountByUserName(
                        SecurityContextHolder.getContext().getAuthentication().getName()));
    }

    public PageResponse<LeaveResponse> list(int page, int size) {
        org.springframework.data.domain.Page<HolidayApply> result =
                requests.findByAccountIdAndIsValidAndApplicantArchivedFalse(
                        current().getId(), 1, ApiPaging.request(page, size));
        Map<Integer, String> names = applicantNames(result.getContent());
        return ApiPaging.map(result, row -> response(row, names));
    }

    public LeaveResponse own(Integer id) {
        HolidayApply request = workflow.findOwnApplication(id);
        LeaveResponse dto = response(request);
        dto.setWorkflowProgress(progress.describe(request));
        dto.setCanWithdraw(withdrawals.canWithdraw(request, current()));
        return dto;
    }

    public LeaveResponse create(LeaveRequest dto) {
        if (!applicantWorkflow.canApply(current()))
            throw new org.springframework.security.access.AccessDeniedException(
                    "System administration accounts cannot apply for leave");
        ApiPaging.required(types.findById(dto.getLeaveTypeId()).orElse(null));
        HolidayApply request = new HolidayApply();
        request.setTitle(dto.getTitle());
        request.setHolidayType(dto.getLeaveTypeId());
        request.setReason(dto.getReason());
        request.setRemark(dto.getComment());
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        request.setTime(
                dto.getStartDate().format(format) + " - " + dto.getEndDate().format(format));
        workflow.saveHolidayApply(request);
        return response(request);
    }

    public void update(Integer id, LeaveRequest dto) {
        workflow.findOwnApplication(id);
        throw new ApiException(
                409, "Submitted leave requests cannot be edited; create a new request");
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Integer id) {
        HolidayApply request = ApiPaging.required(requests.findLockedById(id));
        if (!Objects.equals(request.getAccountId(), current().getId()))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        if (!Objects.equals(request.getIsValid(), 1))
            throw new ApiException(404, "Resource not found");
        if (!Objects.equals(request.getStatus(), 3)
                && !Objects.equals(request.getStatus(), 4)
                && !Objects.equals(request.getStatus(), 5))
            throw new ApiException(
                    409, "Only completed, rejected or withdrawn requests can be archived");
        if (!request.isApplicantArchived()) {
            request.setApplicantArchived(true);
            requests.saveAndFlush(request);
        }
    }

    public List<ApprovalResponse> history(Integer id) {
        HolidayApply request = workflow.findOwnApplication(id);
        if (request.getProcessInstanceId() == null) return Collections.emptyList();
        return approvals
                .findByProcessInstanceIdOrderByCreateTimeAscIdAsc(request.getProcessInstanceId())
                .stream()
                .map(ApiMapper::approval)
                .collect(Collectors.toList());
    }

    /** Personal decisions remain readable independently of applicant list visibility. */
    @PreAuthorize("@backendAuthorization.canApprove(authentication)")
    @Transactional(readOnly = true)
    public PageResponse<ApproverHistoryResponse> approverHistory(int page, int size) {
        var paging = ApiPaging.request(page, size);
        var result =
                approvals.findDecisionHistory(
                        current().getId(),
                        org.springframework.data.domain.PageRequest.of(
                                paging.getPageNumber(), paging.getPageSize()));
        Set<String> processIds =
                result.getContent().stream()
                        .map(HolidayApproval::getProcessInstanceId)
                        .collect(Collectors.toSet());
        List<HolidayApply> rows =
                processIds.isEmpty()
                        ? Collections.emptyList()
                        : requests.findByProcessInstanceIdIn(processIds);
        Map<String, HolidayApply> byProcess = new HashMap<>();
        for (HolidayApply row : rows) {
            if (byProcess.put(row.getProcessInstanceId(), row) != null)
                throw new ApiException(409, "Request history correlation is unavailable");
        }
        Map<Integer, String> names = applicantNames(rows);
        Set<Integer> typeIds =
                rows.stream()
                        .map(HolidayApply::getHolidayType)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Integer, String> typeNames = new HashMap<>();
        if (!typeIds.isEmpty())
            for (HolidayType type : types.findAllById(typeIds))
                typeNames.put(type.getId(), type.getHolidayType());
        return ApiPaging.map(
                result,
                audit -> {
                    HolidayApply row =
                            ApiPaging.required(byProcess.get(audit.getProcessInstanceId()));
                    LeaveResponse leave = response(row, names);
                    ApprovalResponse decision = ApiMapper.approval(audit);
                    ApproverHistoryResponse dto = new ApproverHistoryResponse();
                    dto.setRequestId(row.getId());
                    dto.setTitle(row.getTitle());
                    dto.setApplicantName(leave.getApplicantName());
                    dto.setLeaveType(typeNames.get(row.getHolidayType()));
                    dto.setStartDate(leave.getStartDate());
                    dto.setEndDate(leave.getEndDate());
                    dto.setDecision(decision.getDecision());
                    dto.setStage(decision.getStage());
                    dto.setDecidedAt(decision.getApprovedAt());
                    return dto;
                });
    }

    /** A recorded decision grants read access only to its correlated request. */
    @PreAuthorize("@backendAuthorization.canApprove(authentication)")
    @Transactional(readOnly = true)
    public TaskResponse historicalDetails(Integer id) {
        HolidayApply row = ApiPaging.required(requests.findById(id).orElse(null));
        String processId = row.getProcessInstanceId();
        if (processId == null
                || processId.isBlank()
                || !approvals.existsByProcessInstanceIdAndUserIdAndResultIn(
                        processId, current().getId(), Arrays.asList(1, 2, 3, 4, 5, 6)))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        List<HolidayApply> correlated =
                requests.findByProcessInstanceIdIn(Collections.singleton(processId));
        if (correlated.size() != 1 || !Objects.equals(correlated.get(0).getId(), id))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        TaskResponse dto = new TaskResponse();
        dto.setLeaveRequest(response(row));
        dto.getLeaveRequest().setWorkflowProgress(progress.describe(row));
        dto.setApprovalHistory(
                approvals.findByProcessInstanceIdOrderByCreateTimeAscIdAsc(processId).stream()
                        .map(ApiMapper::approval)
                        .collect(Collectors.toList()));
        return dto;
    }

    @PreAuthorize("@backendAuthorization.canApprove(authentication)")
    public PageResponse<TaskResponse> taskList(int page, int size) {
        ApiPaging.request(page, size);
        MyTaskQuery query = new MyTaskQuery();
        query.setPage(page + 1);
        query.setLimit(size);
        Map<String, Object> result = workflow.queryMyTaskList(query);
        @SuppressWarnings("unchecked")
        List<com.ada.approval.service.projection.LeaveTaskRow> rows =
                (List<com.ada.approval.service.projection.LeaveTaskRow>) result.get("data");
        Map<Integer, String> names = applicantNames(rows);
        List<TaskResponse> content =
                rows.stream()
                        .map(
                                row -> {
                                    TaskResponse dto = new TaskResponse();
                                    dto.setTaskId(row.getTaskId());
                                    dto.setName(ApiMapper.taskName(row.getTaskDefinitionKey()));
                                    dto.setStage(ApiMapper.stage(row.getTaskDefinitionKey()));
                                    dto.setLeaveRequest(response(row, names));
                                    return dto;
                                })
                        .collect(Collectors.toList());
        return new PageResponse<>(content, page, size, ((Number) result.get("count")).longValue());
    }

    @PreAuthorize("@backendAuthorization.canApprove(authentication)")
    public TaskResponse task(String taskId) {
        Task task =
                ApiPaging.required(tasks.createTaskQuery().taskId(taskId).active().singleResult());
        HolidayApply request =
                ApiPaging.required(requests.findByProcessInstanceId(task.getProcessInstanceId()));
        workflow.validateTask(request.getId(), taskId);
        TaskResponse dto = new TaskResponse();
        dto.setTaskId(taskId);
        dto.setName(ApiMapper.taskName(task.getTaskDefinitionKey()));
        dto.setStage(ApiMapper.stage(task.getTaskDefinitionKey()));
        dto.setLeaveRequest(response(request));
        dto.setApprovalHistory(
                approvals
                        .findByProcessInstanceIdOrderByCreateTimeAscIdAsc(
                                request.getProcessInstanceId())
                        .stream()
                        .map(ApiMapper::approval)
                        .collect(Collectors.toList()));
        dto.getLeaveRequest().setWorkflowProgress(progress.describe(request));
        return dto;
    }

    @PreAuthorize("@backendAuthorization.canApprove(authentication)")
    @Transactional(rollbackFor = Exception.class)
    public void decide(String taskId, ApprovalDecision dto, boolean approved) {
        requests.findLockedById(dto.getLeaveRequestId());
        workflow.validateTask(dto.getLeaveRequestId(), taskId);
        workflow.processTask(dto.getLeaveRequestId(), taskId, approved, dto.getComment());
    }
}
