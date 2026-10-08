package com.ada.approval.service.impl;

import cn.hutool.core.date.DateUtil;
import com.ada.approval.enums.HolidayApplyStatus;
import com.ada.approval.enums.HolidayApprovalStatus;
import com.ada.approval.entity.Account;
import com.ada.approval.entity.Employee;
import com.ada.approval.entity.HolidayApply;
import com.ada.approval.repository.HolidayApplyRepository;
import com.ada.approval.service.projection.LeaveTaskRow;
import org.springframework.beans.BeanUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Collections;
import com.ada.approval.entity.HolidayApproval;
import com.ada.approval.query.HolidayApplyQuery;
import com.ada.approval.query.MyTaskQuery;
import com.ada.approval.service.IAccountService;
import com.ada.approval.service.IEmployeeService;
import com.ada.approval.service.IHolidayApplyService;
import com.ada.approval.service.IHolidayApprovalService;
import com.ada.approval.utils.AssertUtil;
import com.ada.approval.utils.PageResultUtil;
import com.ada.approval.workflow.WorkflowConstant;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coordinates JPA leave records with Activiti task and process operations. Ownership and
 * task-specific checks are authoritative; transitions share the application transaction manager.
 */
@Service
public class HolidayApplyServiceImpl extends JpaCrudService<HolidayApply>
        implements IHolidayApplyService {
    private final HolidayApplyRepository holidayApplyRepository;

    public HolidayApplyServiceImpl(HolidayApplyRepository repository) {
        super(repository);
        this.holidayApplyRepository = repository;
    }

    @Autowired private IAccountService accountService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private org.activiti.engine.RepositoryService processDefinitions;
    @Autowired private TaskService taskService;
    @Autowired private IEmployeeService employeeService;

    @Autowired private IHolidayApprovalService holidayApprovalService;
    @Autowired private com.ada.approval.workflow.ApplicantWorkflow applicantWorkflow;

    /**
     * Persists a new leave request and starts its process using the latest compatible definition.
     * Initial task lookup is scoped to that new instance; generated application IDs become process
     * business keys.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void saveHolidayApply(HolidayApply holidayApply) {
        AssertUtil.isTrue(
                holidayApply.getId() != null, "A new leave request must not specify an ID");
        // 1.Validate required form fields
        checkParams(
                holidayApply.getTitle(), holidayApply.getHolidayType(), holidayApply.getReason());
        // 2.Validate leave dates and calculate duration
        setHolidays(holidayApply);
        // 3.Save the leave request
        // Find the authenticated user
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        Account account = accountService.findAccountByUserName(name);
        Map<String, Object> map = applicantWorkflow.variables(account, holidayApply.getDays());
        org.activiti.engine.repository.ProcessDefinition definition =
                processDefinitions
                        .createProcessDefinitionQuery()
                        .processDefinitionKey(
                                WorkflowConstant.EMPLOYEE_HOLILDAY_PROCESS_DEFINITION_KEY)
                        .latestVersion()
                        .singleResult();
        org.activiti.bpmn.model.BpmnModel deployed =
                definition == null ? null : processDefinitions.getBpmnModel(definition.getId());
        if (deployed == null
                || deployed.getMainProcess() == null
                || !(deployed.getMainProcess().getFlowElement("applicant_route")
                        instanceof org.activiti.bpmn.model.ExclusiveGateway))
            throw new com.ada.approval.api.error.ApiException(
                    409,
                    "The applicant-aware leave workflow must be deployed before submitting leave");
        holidayApply.setAccountId(account.getId());
        holidayApply.setIsValid(1); // Valid by default
        holidayApply.setStatus(
                HolidayApplyStatus.SUBMIT.getType()); // Submitted leave request status
        holidayApply.setSubmitTime(new Date()); // Record the server submission time
        boolean save = this.save(holidayApply);
        AssertUtil.isTrue(!save, "Unable to create leave request");
        // 4.Start the process with the leave request ID as business key
        String businessKey = holidayApply.getId().toString();
        // All required approvers were validated before persisting or starting the process.
        ProcessInstance processInstance =
                runtimeService.startProcessInstanceByKey(
                        WorkflowConstant.EMPLOYEE_HOLILDAY_PROCESS_DEFINITION_KEY,
                        businessKey,
                        map);
        // Get the process instance ID
        String processInstanceId = processInstance.getProcessInstanceId();
        holidayApply.setProcessInstanceId(processInstanceId);
        // 5.Store the process instance ID on the leave request
        boolean b = this.updateById(holidayApply);
        AssertUtil.isTrue(!b, "Unable to start leave workflow");

        // 6.Complete the submission task and assign the next approver
        Task task =
                taskService
                        .createTaskQuery()
                        .processInstanceId(processInstanceId)
                        .taskAssignee(name)
                        .active()
                        .singleResult();
        AssertUtil.isTrue(task == null, "Initial submission task not found");
        taskService.complete(task.getId(), map);
    }

    @Override
    public Map<String, Object> queryMyHolidayApply(HolidayApplyQuery holidayApplyQuery) {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        Account account = accountService.findAccountByUserName(name);
        List<HolidayApply> records =
                holidayApplyRepository.findMyApplications(
                        account.getId(),
                        optionalFilter(holidayApplyQuery.getTitle()),
                        optionalFilter(holidayApplyQuery.getStartTime()),
                        optionalFilter(holidayApplyQuery.getEndTime()),
                        optionalFilter(holidayApplyQuery.getStatus()));
        // Preserve selectPage without a configured pagination interceptor: all rows and total zero.
        return PageResultUtil.getResult(0L, records);
    }

    @Override
    public HolidayApply findOwnApplication(Integer id) {
        HolidayApply application = getById(id);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Account current = accountService.findAccountByUserName(username);
        if (application == null
                || current == null
                || !Objects.equals(application.getAccountId(), current.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Not your leave request");
        }
        return application;
    }

    @Override
    public void deleteHolidayApply(Integer id) {
        // Find the leave request by ID
        HolidayApply holidayApply = findOwnApplication(id);
        // Validate
        AssertUtil.isTrue(null == holidayApply, "Leave request not found");
        // In-review leave requests cannot be deleted
        AssertUtil.isTrue(
                holidayApply.getStatus() == HolidayApplyStatus.SUBMIT.getType()
                        || holidayApply.getStatus() == HolidayApplyStatus.APPROVAL.getType(),
                "Submitted or in-review leave requests cannot be deleted");
        holidayApply.setIsValid(0); // Soft deletion
        // Delete the record
        boolean b = this.updateById(holidayApply);
        AssertUtil.isTrue(!b, "Unable to delete leave request");
    }

    @Override
    @org.springframework.security.access.prepost.PreAuthorize(
            "@backendAuthorization.canApprove(authentication)")
    public Map<String, Object> queryMyTaskList(MyTaskQuery myTaskQuery) {
        List<LeaveTaskRow> rows = currentTaskRows();
        int page = myTaskQuery.getPage() == null ? 1 : myTaskQuery.getPage();
        int limit = myTaskQuery.getLimit() == null ? 10 : myTaskQuery.getLimit();
        AssertUtil.isTrue(page < 1 || limit < 1, "Invalid pagination parameters");
        long offset = ((long) page - 1) * limit;
        int from = (int) Math.min(offset, rows.size());
        int to = (int) Math.min((long) from + limit, rows.size());
        return PageResultUtil.getResult(rows.size(), rows.subList(from, to));
    }

    @Override
    @org.springframework.security.access.prepost.PreAuthorize(
            "@backendAuthorization.canApprove(authentication)")
    public void validateTask(Integer holidayApplyId, String taskId) {
        checkedTask(holidayApplyId, taskId);
    }

    /**
     * Requires the supplied task to belong to this request and the current account to be its
     * assignee or candidate. An approval-function grant alone is insufficient, and applicants
     * cannot approve their own requests.
     */
    private Task checkedTask(Integer holidayApplyId, String taskId) {
        HolidayApply application = getById(holidayApplyId);
        AssertUtil.isTrue(
                application == null || !Objects.equals(application.getIsValid(), 1),
                "Leave request not found or deleted");
        AssertUtil.isTrue(
                !Objects.equals(application.getStatus(), HolidayApplyStatus.SUBMIT.getType())
                        && !Objects.equals(
                                application.getStatus(), HolidayApplyStatus.APPROVAL.getType()),
                "Leave workflow has ended");
        AssertUtil.isTrue(StringUtils.isBlank(taskId), "Approval task is required");
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        AssertUtil.isTrue(task == null, "Approval task not found or suspended");
        AssertUtil.isTrue(
                StringUtils.isBlank(application.getProcessInstanceId())
                        || !Objects.equals(
                                application.getProcessInstanceId(), task.getProcessInstanceId()),
                "Task does not belong to this leave workflow");
        String key = task.getTaskDefinitionKey();
        AssertUtil.isTrue(
                !WorkflowConstant.EMPLOYEE_HOLIDAY_MANAGER_TASK_DEF_KEY.equals(key)
                        && !WorkflowConstant.EMPLOYEE_HOLIDAY_BOSS_TASK_DEF_KEY.equals(key)
                        && !WorkflowConstant.EMPLOYEE_HOLIDAY_HR_TASK_DEF_KEY.equals(key),
                "Task is not an approval task");
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Account approver = accountService.findAccountByUserName(username);
        if (approver == null || Objects.equals(application.getAccountId(), approver.getId()))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Applicants cannot approve their own leave requests");
        if (StringUtils.isNotBlank(task.getAssignee())) {
            if (!username.equals(task.getAssignee()))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Not this task assignee");
        } else {
            long candidates =
                    taskService
                            .createTaskQuery()
                            .taskId(taskId)
                            .active()
                            .taskCandidateUser(username)
                            .count();
            if (candidates == 0)
                throw new org.springframework.security.access.AccessDeniedException(
                        "Not this task candidate");
        }
        return task;
    }

    /**
     * Records a decision and advances or terminates the corresponding process in one shared
     * transaction. Approval completes the task; rejection deletes runtime execution without
     * completing the rejected task.
     */
    @Override
    // Atomic across JPA and Activiti only when both share the Spring transaction manager/data
    // source.
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @org.springframework.security.access.prepost.PreAuthorize(
            "@backendAuthorization.canApprove(authentication)")
    public void processTask(
            Integer holidayApplyId, String taskId, boolean stepPass, String remark) {
        HolidayApply application = holidayApplyRepository.findLockedById(holidayApplyId);
        AssertUtil.isTrue(application == null, "Leave request not found");
        Task task = checkedTask(holidayApplyId, taskId);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Account account = accountService.findAccountByUserName(username);
        AssertUtil.isTrue(account == null, "Current account not found");
        if (StringUtils.isBlank(task.getAssignee())) taskService.claim(taskId, username);
        HolidayApproval audit = new HolidayApproval();
        audit.setProcessInstanceId(application.getProcessInstanceId());
        audit.setTaskId(taskId);
        audit.setTaskDefKey(task.getTaskDefinitionKey());
        audit.setUserId(account.getId());
        audit.setUserName(username);
        audit.setRemark(remark);
        audit.setCreateTime(LocalDateTime.now());
        Map<String, Object> variables = new HashMap<>();
        switch (task.getTaskDefinitionKey()) {
            case WorkflowConstant.EMPLOYEE_HOLIDAY_MANAGER_TASK_DEF_KEY:
                audit.setResult(
                        stepPass
                                ? HolidayApprovalStatus.MANAGER_AGREE.getType()
                                : HolidayApprovalStatus.MANAGER_DISAGREE.getType());
                if (stepPass
                        && runtimeService.getVariable(
                                        application.getProcessInstanceId(),
                                        com.ada.approval.workflow.ApplicantWorkflow
                                                .IDENTITY_VARIABLE)
                                == null) {
                    AssertUtil.isTrue(
                            application.getDays() == null || application.getDays() < 1,
                            "Invalid leave duration");
                    variables.put("day", application.getDays());
                    if (application.getDays() > 3) {
                        Employee boss = employeeService.findBoss();
                        AssertUtil.isTrue(
                                boss == null || StringUtils.isBlank(boss.getEmail()),
                                "General Manager is not configured");
                        variables.put("boss", boss.getEmail());
                    }
                    List<Employee> hrs = employeeService.findAllHrs();
                    AssertUtil.isTrue(
                            hrs == null || hrs.isEmpty(), "HR approvers are not configured");
                    List<String> emails =
                            hrs.stream()
                                    .map(Employee::getEmail)
                                    .filter(StringUtils::isNotBlank)
                                    .collect(Collectors.toList());
                    AssertUtil.isTrue(emails.isEmpty(), "HR approvers are not configured");
                    variables.put("hrs", StringUtils.join(emails, ","));
                }
                break;
            case WorkflowConstant.EMPLOYEE_HOLIDAY_BOSS_TASK_DEF_KEY:
                audit.setResult(
                        stepPass
                                ? HolidayApprovalStatus.BOSS_AGREE.getType()
                                : HolidayApprovalStatus.BOSS_DISAGREE.getType());
                break;
            case WorkflowConstant.EMPLOYEE_HOLIDAY_HR_TASK_DEF_KEY:
                audit.setResult(
                        stepPass
                                ? HolidayApprovalStatus.HR_AGREE.getType()
                                : HolidayApprovalStatus.HR_DISAGREE.getType());
                break;
            default:
                throw new IllegalStateException("Unsupported approval task");
        }
        application.setApprovalStatus(audit.getResult());
        application.setStatus(
                !stepPass
                        ? HolidayApplyStatus.REJECTED.getType()
                        : (WorkflowConstant.EMPLOYEE_HOLIDAY_HR_TASK_DEF_KEY.equals(
                                                task.getTaskDefinitionKey())
                                        || (WorkflowConstant.EMPLOYEE_HOLIDAY_BOSS_TASK_DEF_KEY
                                                        .equals(task.getTaskDefinitionKey())
                                                && "HR"
                                                        .equals(
                                                                runtimeService.getVariable(
                                                                        application
                                                                                .getProcessInstanceId(),
                                                                        com.ada.approval.workflow
                                                                                .ApplicantWorkflow
                                                                                .IDENTITY_VARIABLE))))
                                ? HolidayApplyStatus.COMPLETED.getType()
                                : HolidayApplyStatus.APPROVAL.getType());
        AssertUtil.isTrue(!holidayApprovalService.save(audit), "Unable to save approval record");
        AssertUtil.isTrue(!updateById(application), "Unable to update leave request");
        if (stepPass) taskService.complete(taskId, variables);
        else
            runtimeService.deleteProcessInstance(
                    application.getProcessInstanceId(),
                    "Leave rejected: " + (remark == null ? "" : remark));
    }

    private List<LeaveTaskRow> currentTaskRows() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Task> tasks =
                taskService
                        .createTaskQuery()
                        .processDefinitionKey(
                                WorkflowConstant.EMPLOYEE_HOLILDAY_PROCESS_DEFINITION_KEY)
                        .active()
                        .taskCandidateOrAssigned(username)
                        .orderByTaskCreateTime()
                        .asc()
                        .list();
        if (tasks.isEmpty()) return Collections.emptyList();
        List<String> processIds =
                tasks.stream()
                        .map(Task::getProcessInstanceId)
                        .distinct()
                        .collect(Collectors.toList());
        Map<String, HolidayApply> byProcess =
                holidayApplyRepository.findByProcessInstanceIdIn(processIds).stream()
                        .filter(
                                h ->
                                        Objects.equals(h.getIsValid(), 1)
                                                && (Objects.equals(
                                                                h.getStatus(),
                                                                HolidayApplyStatus.SUBMIT.getType())
                                                        || Objects.equals(
                                                                h.getStatus(),
                                                                HolidayApplyStatus.APPROVAL
                                                                        .getType())))
                        .collect(Collectors.toMap(HolidayApply::getProcessInstanceId, h -> h));
        List<LeaveTaskRow> rows = new ArrayList<>();
        for (Task task : tasks) {
            HolidayApply application = byProcess.get(task.getProcessInstanceId());
            String key = task.getTaskDefinitionKey();
            if (application == null
                    || !(WorkflowConstant.EMPLOYEE_HOLIDAY_MANAGER_TASK_DEF_KEY.equals(key)
                            || WorkflowConstant.EMPLOYEE_HOLIDAY_BOSS_TASK_DEF_KEY.equals(key)
                            || WorkflowConstant.EMPLOYEE_HOLIDAY_HR_TASK_DEF_KEY.equals(key)))
                continue;
            LeaveTaskRow row = new LeaveTaskRow();
            BeanUtils.copyProperties(application, row);
            row.setTaskDefinitionKey(task.getTaskDefinitionKey());
            row.setTaskId(task.getId());
            row.setAssignee(task.getAssignee());
            row.setActName(task.getName());
            rows.add(row);
        }
        return rows;
    }

    @Override
    @org.springframework.security.access.prepost.PreAuthorize(
            "@backendAuthorization.canApprove(authentication)")
    public int countMyTask() {
        return currentTaskRows().size();
    }

    /**
     * Parse the submitted date range and calculate leave duration
     *
     * @param holidayApply
     */
    private String optionalFilter(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private void setHolidays(HolidayApply holidayApply) {
        // Validate required fields first
        AssertUtil.isTrue(StringUtils.isBlank(holidayApply.getTime()), "Leave dates are required");
        String time = holidayApply.getTime(); // Leave date range
        // Split the date range into start and end values
        String[] times = time.split(" - ");
        // Leave start date
        Date startTime = DateUtil.parse(times[0].trim());
        // Leave end date
        Date endTime = DateUtil.parse(times[1].trim());
        // End may equal start for a one-day request, but cannot precede it.
        int compare = DateUtil.compare(endTime, startTime);
        AssertUtil.isTrue(compare < 0, "End date must not precede start date");

        // Inclusive calendar dates, independent of 23/25-hour daylight-saving days.
        long days =
                java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.parse(DateUtil.formatDate(startTime)),
                        java.time.LocalDate.parse(DateUtil.formatDate(endTime)));

        holidayApply.setStartTime(startTime);
        holidayApply.setEndTime(endTime);
        holidayApply.setDays((int) days + 1);
    }

    /**
     * Validate required form fields
     *
     * @param title
     * @param holidayType
     * @param reason
     */
    private void checkParams(String title, Integer holidayType, String reason) {
        AssertUtil.isTrue(StringUtils.isBlank(title), "Leave request title is required");
        AssertUtil.isTrue(null == holidayType, "Leave type is required");
        AssertUtil.isTrue(StringUtils.isBlank(reason), "Leave reason is required");
    }
}
