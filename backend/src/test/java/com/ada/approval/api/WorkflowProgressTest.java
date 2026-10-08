package com.ada.approval.api;

import com.ada.approval.api.service.WorkflowProgressService;
import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.entity.*;
import com.ada.approval.repository.HolidayApprovalRepository;
import org.activiti.engine.*;
import org.activiti.engine.task.Task;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Engine/repository mocks only: no Spring context, database or workflow execution. */
class WorkflowProgressTest {
    private final TaskService tasks = mock(TaskService.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final HolidayApprovalRepository approvals = mock(HolidayApprovalRepository.class);
    private final WorkflowProgressService service =
            new WorkflowProgressService(tasks, runtime, history, approvals);
    private final org.activiti.engine.task.TaskQuery taskQuery =
            mock(org.activiti.engine.task.TaskQuery.class, RETURNS_SELF);
    private final org.activiti.engine.runtime.ProcessInstanceQuery processQuery =
            mock(org.activiti.engine.runtime.ProcessInstanceQuery.class, RETURNS_SELF);
    private final org.activiti.engine.history.HistoricProcessInstanceQuery historyQuery =
            mock(org.activiti.engine.history.HistoricProcessInstanceQuery.class, RETURNS_SELF);
    private HolidayApply request;

    @BeforeEach
    void setup() {
        when(tasks.createTaskQuery()).thenReturn(taskQuery);
        when(runtime.createProcessInstanceQuery()).thenReturn(processQuery);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(historyQuery);
        request = new HolidayApply();
        request.setProcessInstanceId("process");
        request.setDays(3);
        request.setStatus(1);
        when(approvals.findByProcessInstanceIdOrderByCreateTimeAscIdAsc("process"))
                .thenReturn(List.of());
        when(runtime.getVariable("process", "day")).thenReturn(null);
    }

    private void current(String key) {
        Task task = mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn(key);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(processQuery.singleResult()).thenReturn(mock(ProcessInstance.class));
    }

    private void ended(boolean rejected) {
        when(processQuery.singleResult()).thenReturn(null);
        when(taskQuery.list()).thenReturn(List.of());
        HistoricProcessInstance historic = mock(HistoricProcessInstance.class);
        when(historic.getEndTime()).thenReturn(new Date());
        when(historic.getDeleteReason()).thenReturn(rejected ? "rejected" : null);
        when(historyQuery.singleResult()).thenReturn(historic);
    }

    private List<String> keys(WorkflowProgress p) {
        return p.getStages().stream().map(WorkflowProgressStage::getStage).toList();
    }

    private String state(WorkflowProgress p, String key) {
        return p.getStages().stream()
                .filter(s -> s.getStage().equals(key))
                .findFirst()
                .orElseThrow()
                .getStatus();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void shortLeaveStartsWithManagerWithoutGeneralManager(int days) {
        request.setDays(days);
        current("manager_check");
        var p = service.describe(request);
        assertEquals(List.of("SUBMITTED", "DEPARTMENT_MANAGER", "HR", "COMPLETED"), keys(p));
        assertEquals("DEPARTMENT_MANAGER", p.getCurrentStage());
        assertEquals("CURRENT", state(p, "DEPARTMENT_MANAGER"));
        verify(taskQuery).processInstanceId("process");
        assertEquals("COMPLETED", state(p, "SUBMITTED"));
        assertEquals("Awaiting Department Manager approval", p.getMessage());
    }

    @Test
    void longLeaveIncludesUpcomingGeneralManager() {
        request.setDays(4);
        current("manager_check");
        var p = service.describe(request);
        assertEquals("UPCOMING", state(p, "GENERAL_MANAGER"));
    }

    @Test
    void generalManagerCurrentUsesLiveTaskRatherThanApplicationStatus() {
        request.setDays(4);
        current("boss_check");
        var p = service.describe(request);
        assertEquals("GENERAL_MANAGER", p.getCurrentStage());
        assertEquals("COMPLETED", state(p, "DEPARTMENT_MANAGER"));
        assertEquals("Awaiting General Manager approval", p.getMessage());
        assertEquals("UPCOMING", state(p, "HR"));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void hrIsCurrentAfterEarlierStages(int days) {
        request.setDays(days);
        current("hr_check");
        var p = service.describe(request);
        assertEquals("HR", p.getCurrentStage());
        assertEquals("CURRENT", state(p, "HR"));
        assertEquals("COMPLETED", state(p, "DEPARTMENT_MANAGER"));
        if (days > 3) assertEquals("COMPLETED", state(p, "GENERAL_MANAGER"));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void successfulEngineCompletionMarksEveryStageCompleted(int days) {
        request.setDays(days);
        request.setStatus(4);
        ended(false);
        var p = service.describe(request);
        assertEquals("COMPLETED", p.getCurrentStage());
        assertEquals("Approval workflow completed", p.getMessage());
        assertTrue(p.getStages().stream().allMatch(s -> s.getStatus().equals("COMPLETED")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manager_check", "boss_check", "hr_check"})
    void rejectionCancelsLaterStages(String key) {
        request.setDays(4);
        request.setStatus(3);
        ended(true);
        HolidayApproval decision = new HolidayApproval();
        decision.setTaskDefKey(key);
        decision.setResult(key.equals("manager_check") ? 2 : key.equals("boss_check") ? 4 : 6);
        when(approvals.findByProcessInstanceIdOrderByCreateTimeAscIdAsc("process"))
                .thenReturn(List.of(decision));
        var p = service.describe(request);
        assertEquals("REJECTED", p.getCurrentStage());
        String stage = com.ada.approval.api.dto.ApiMapper.stage(key);
        assertEquals("REJECTED", state(p, stage));
        assertEquals("NOT_REACHED", state(p, "COMPLETED"));
        assertTrue(
                p.getStages().stream()
                        .noneMatch(s -> Set.of("CURRENT", "UPCOMING").contains(s.getStatus())));
        assertEquals(
                "Request rejected by "
                        + p.getStages().stream()
                                .filter(s -> s.getStage().equals(stage))
                                .findFirst()
                                .orElseThrow()
                                .getLabel(),
                p.getMessage());
    }

    @Test
    void missingEngineEvidenceDoesNotInventAnApprovalStage() {
        request.setProcessInstanceId(null);
        var p = service.describe(request);
        assertEquals("UNKNOWN", p.getCurrentStage());
        verifyNoInteractions(tasks, runtime, history, approvals);
    }

    @Test
    void runtimeRoutingVariableIsRespected() {
        current("hr_check");
        when(runtime.getVariable("process", "day")).thenReturn(4);
        assertTrue(keys(service.describe(request)).contains("GENERAL_MANAGER"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "EMPLOYEE,2,hr_check,DEPARTMENT_MANAGER:HR",
        "EMPLOYEE,3,hr_check,DEPARTMENT_MANAGER:HR",
        "EMPLOYEE,4,boss_check,DEPARTMENT_MANAGER:GENERAL_MANAGER:HR",
        "DEPARTMENT_MANAGER,2,boss_check,GENERAL_MANAGER:HR",
        "DEPARTMENT_MANAGER,4,hr_check,GENERAL_MANAGER:HR",
        "GENERAL_MANAGER,2,hr_check,HR",
        "HR,4,boss_check,GENERAL_MANAGER"
    })
    void roleAwareProgressUsesStoredIdentityAndLiveTask(
            String identity, int days, String key, String route) {
        request.setDays(days);
        request.setStatus(2);
        current(key);
        when(runtime.getVariable("process", "applicantWorkflowIdentity")).thenReturn(identity);
        var p = service.describe(request);
        var expected = new ArrayList<String>();
        expected.add("SUBMITTED");
        expected.addAll(List.of(route.split(":")));
        expected.add("COMPLETED");
        assertEquals(expected, keys(p));
        assertEquals(com.ada.approval.api.dto.ApiMapper.stage(key), p.getCurrentStage());
        assertEquals("CURRENT", state(p, p.getCurrentStage()));
    }

    private void historicIdentity(String identity) {
        var q = mock(org.activiti.engine.history.HistoricVariableInstanceQuery.class, RETURNS_SELF);
        var v = mock(org.activiti.engine.history.HistoricVariableInstance.class);
        when(history.createHistoricVariableInstanceQuery()).thenReturn(q);
        when(q.singleResult()).thenReturn(v);
        when(v.getValue()).thenReturn(identity);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "DEPARTMENT_MANAGER", "GENERAL_MANAGER", "HR"})
    void endedRoutesUseHistoricIdentityRatherThanCurrentEmployeeTitle(String identity) {
        request.setStatus(4);
        request.setDays(2);
        ended(false);
        historicIdentity(identity);
        var p = service.describe(request);
        var expected = new ArrayList<String>();
        expected.add("SUBMITTED");
        expected.addAll(
                com.ada.approval.workflow.ApplicantWorkflow.stages(
                        com.ada.approval.workflow.ApplicantWorkflow.Identity.valueOf(identity), 2));
        expected.add("COMPLETED");
        assertEquals(expected, keys(p));
        assertEquals("COMPLETED", p.getCurrentStage());
        assertTrue(p.getStages().stream().allMatch(s -> "COMPLETED".equals(s.getStatus())));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "DEPARTMENT_MANAGER,boss_check,4",
        "DEPARTMENT_MANAGER,hr_check,6",
        "GENERAL_MANAGER,hr_check,6",
        "HR,boss_check,4"
    })
    void rejectedRoleRouteCancelsOnlyItsRemainingStages(
            String identity, String taskKey, int result) {
        request.setStatus(3);
        request.setDays(2);
        ended(true);
        historicIdentity(identity);
        HolidayApproval a = new HolidayApproval();
        a.setTaskDefKey(taskKey);
        a.setResult(result);
        when(approvals.findByProcessInstanceIdOrderByCreateTimeAscIdAsc("process"))
                .thenReturn(List.of(a));
        var p = service.describe(request);
        assertEquals("REJECTED", p.getCurrentStage());
        assertEquals("REJECTED", state(p, com.ada.approval.api.dto.ApiMapper.stage(taskKey)));
        assertEquals("NOT_REACHED", state(p, "COMPLETED"));
        assertTrue(
                p.getStages().stream()
                        .noneMatch(
                                s ->
                                        "CURRENT".equals(s.getStatus())
                                                || "UPCOMING".equals(s.getStatus())));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "EMPLOYEE,2,manager_check",
        "EMPLOYEE,2,hr_check",
        "EMPLOYEE,4,boss_check",
        "EMPLOYEE,4,hr_check",
        "DEPARTMENT_MANAGER,2,boss_check",
        "GENERAL_MANAGER,2,hr_check",
        "HR,2,boss_check"
    })
    void withdrawnProgressPreservesRouteAndActualApprovedHistory(
            String identity, int days, String key) {
        request.setDays(days);
        request.setStatus(5);
        ended(true);
        historicIdentity(identity);
        var decisions = new ArrayList<HolidayApproval>();
        if (identity.equals("EMPLOYEE") && !key.equals("manager_check")) {
            var manager = new HolidayApproval();
            manager.setTaskDefKey("manager_check");
            manager.setResult(1);
            decisions.add(manager);
        }
        if (identity.equals("EMPLOYEE") && days > 3 && key.equals("hr_check")) {
            var boss = new HolidayApproval();
            boss.setTaskDefKey("boss_check");
            boss.setResult(3);
            decisions.add(boss);
        }
        var withdrawal = new HolidayApproval();
        withdrawal.setTaskDefKey(key);
        withdrawal.setResult(7);
        decisions.add(withdrawal);
        when(approvals.findByProcessInstanceIdOrderByCreateTimeAscIdAsc("process"))
                .thenReturn(decisions);
        var p = service.describe(request);
        assertEquals("WITHDRAWN", p.getCurrentStage());
        assertEquals("Request withdrawn by applicant", p.getMessage());
        assertEquals("NOT_REACHED", state(p, com.ada.approval.api.dto.ApiMapper.stage(key)));
        assertEquals("WITHDRAWN", state(p, "WITHDRAWN"));
        assertFalse(keys(p).contains("COMPLETED"));
        assertTrue(
                p.getStages().stream()
                        .noneMatch(
                                s ->
                                        Set.of("CURRENT", "UPCOMING", "REJECTED")
                                                .contains(s.getStatus())));
        var expected = new ArrayList<String>();
        expected.add("SUBMITTED");
        expected.addAll(
                com.ada.approval.workflow.ApplicantWorkflow.stages(
                        com.ada.approval.workflow.ApplicantWorkflow.Identity.valueOf(identity),
                        days));
        expected.add("WITHDRAWN");
        assertEquals(expected, keys(p));
        if (decisions.size() > 1) assertEquals("COMPLETED", state(p, "DEPARTMENT_MANAGER"));
        if (decisions.size() > 2) assertEquals("COMPLETED", state(p, "GENERAL_MANAGER"));
    }

    @Test
    void inconsistentWithdrawalDoesNotFabricateProgress() {
        request.setStatus(5);
        ended(true);
        var p = service.describe(request);
        assertEquals("UNKNOWN", p.getCurrentStage());
    }
}
