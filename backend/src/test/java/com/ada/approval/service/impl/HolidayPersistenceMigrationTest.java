package com.ada.approval.service.impl;

import com.ada.approval.exception.ParamException;
import com.ada.approval.entity.*;
import com.ada.approval.query.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.*;
import com.ada.approval.workflow.WorkflowConstant;
import org.activiti.engine.*;
import org.activiti.engine.task.*;
import org.activiti.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Persistence tests with mocks only; no Spring context or Activiti engine. */
class HolidayPersistenceMigrationTest {
    private final HolidayApplyRepository applications = mock(HolidayApplyRepository.class);
    private final HolidayApprovalRepository approvals = mock(HolidayApprovalRepository.class);
    private final IAccountService accounts = mock(IAccountService.class);
    private final HolidayApplyServiceImpl service = new HolidayApplyServiceImpl(applications);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "accountService", accounts);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("user@example.com", "unused"));
        Account account = new Account();
        account.setId(8);
        account.setEmpId(80);
        when(accounts.findAccountByUserName("user@example.com")).thenReturn(account);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private HolidayApply application(int status) {
        HolidayApply h = new HolidayApply();
        h.setId(4);
        h.setAccountId(8);
        h.setStatus(status);
        h.setIsValid(1);
        h.setTitle("Original");
        return h;
    }

    @Test
    void dateOnlyRangesCountCalendarDaysIncludingBothEndpointsAndDstTransitions() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
            for (String[] example :
                    new String[][] {
                        {"2026-11-01", "2026-11-01", "1"},
                        {"2026-11-01", "2026-11-03", "3"},
                        {"2026-11-01", "2026-11-04", "4"},
                        {"2026-03-28", "2026-03-30", "3"},
                        {"2026-10-24", "2026-10-26", "3"}
                    }) {
                HolidayApply request = new HolidayApply();
                request.setTime(example[0] + " 00:00:00 - " + example[1] + " 00:00:00");
                ReflectionTestUtils.invokeMethod(service, "setHolidays", request);
                assertEquals(Integer.valueOf(example[2]), request.getDays());
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void reversedDatesAreRejectedBeforePersistence() {
        HolidayApply request = new HolidayApply();
        request.setTime("2026-11-02 00:00:00 - 2026-11-01 00:00:00");
        assertThrows(
                ParamException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "setHolidays", request));
        verifyNoInteractions(applications);
    }

    @Test
    void listUsesAccountAndExistingStringFilters() {
        HolidayApplyQuery q = new HolidayApplyQuery();
        q.setPage(2);
        q.setLimit(1);
        q.setTitle("Leave");
        q.setStartTime("2026-01-01");
        q.setEndTime("2026-01-31");
        q.setStatus("4");
        List<HolidayApply> rows = Arrays.asList(application(4), application(4));
        when(applications.findMyApplications(8, "Leave", "2026-01-01", "2026-01-31", "4"))
                .thenReturn(rows);
        Map<String, Object> result = service.queryMyHolidayApply(q);
        assertEquals(0, result.get("code"));
        assertEquals("", result.get("msg"));
        assertEquals(0L, result.get("count"));
        assertSame(rows, result.get("data"));
    }

    @Test
    void blankListFiltersAreOmitted() {
        HolidayApplyQuery q = new HolidayApplyQuery();
        q.setTitle("  ");
        q.setStatus("");
        when(applications.findMyApplications(8, null, null, null, null))
                .thenReturn(Collections.emptyList());
        service.queryMyHolidayApply(q);
        verify(applications).findMyApplications(8, null, null, null, null);
    }

    @Test
    void applicationCrudReadsAndFlushesThroughJpa() {
        HolidayApply h = application(4);
        when(applications.findById(4)).thenReturn(Optional.of(h));
        assertSame(h, service.getById(4));
        assertTrue(service.save(h));
        verify(applications).saveAndFlush(h);
    }

    @Test
    void partialApplicationUpdatePreservesOtherFields() {
        HolidayApply existing = application(4);
        when(applications.findById(4)).thenReturn(Optional.of(existing));
        HolidayApply patch = new HolidayApply();
        patch.setId(4);
        patch.setRemark("Updated");
        assertTrue(service.updateById(patch));
        assertEquals("Original", existing.getTitle());
        assertEquals(Integer.valueOf(8), existing.getAccountId());
        assertEquals("Updated", existing.getRemark());
        verify(applications).saveAndFlush(existing);
    }

    @Test
    void completedApplicationIsSoftDeleted() {
        HolidayApply h = application(4);
        when(applications.findById(4)).thenReturn(Optional.of(h));
        service.deleteHolidayApply(4);
        assertEquals(Integer.valueOf(0), h.getIsValid());
        verify(applications).saveAndFlush(h);
        verify(applications, never()).delete(any());
    }

    @Test
    void submittedAndApprovingApplicationsCannotBeDeleted() {
        for (int status : new int[] {1, 2}) {
            when(applications.findById(4)).thenReturn(Optional.of(application(status)));
            assertThrows(ParamException.class, () -> service.deleteHolidayApply(4));
        }
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void missingApplicationCannotBeDeleted() {
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.deleteHolidayApply(4));
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void approvalCrudUsesJpaWithPartialUpdate() {
        HolidayApprovalServiceImpl s = new HolidayApprovalServiceImpl(approvals);
        HolidayApproval a = new HolidayApproval();
        a.setId(6);
        a.setProcessInstanceId("process");
        a.setUserId(8);
        when(approvals.findById(6)).thenReturn(Optional.of(a));
        assertTrue(s.save(a));
        HolidayApproval patch = new HolidayApproval();
        patch.setId(6);
        patch.setRemark("Reviewed");
        assertTrue(s.updateById(patch));
        assertEquals("process", a.getProcessInstanceId());
        assertEquals(Integer.valueOf(8), a.getUserId());
        assertEquals("Reviewed", a.getRemark());
        verify(approvals, times(2)).saveAndFlush(a);
    }

    @Test
    void submissionFlushesIdsAndRestrictsInitialTaskToNewProcess() {
        RuntimeService runtime = mock(RuntimeService.class);
        TaskService tasks = mock(TaskService.class);
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        Task task = mock(Task.class);
        ProcessInstance process = mock(ProcessInstance.class);
        IEmployeeService employees = mock(IEmployeeService.class);
        ReflectionTestUtils.setField(service, "runtimeService", runtime);
        ReflectionTestUtils.setField(service, "taskService", tasks);
        ReflectionTestUtils.setField(service, "employeeService", employees);
        HolidayApply h = new HolidayApply();
        h.setTitle("Leave");
        h.setReason("Reason");
        h.setHolidayType(1);
        h.setTime("2026-01-01 - 2026-01-02");
        when(applications.saveAndFlush(any()))
                .thenAnswer(
                        i -> {
                            HolidayApply row = i.getArgument(0);
                            row.setId(4);
                            return row;
                        });
        when(applications.findById(4)).thenReturn(Optional.of(h));
        when(runtime.startProcessInstanceByKey(
                        eq(WorkflowConstant.EMPLOYEE_HOLILDAY_PROCESS_DEFINITION_KEY),
                        eq("4"),
                        anyMap()))
                .thenReturn(process);
        when(process.getProcessInstanceId()).thenReturn("process");
        when(tasks.createTaskQuery()).thenReturn(taskQuery);
        doReturn(task).when(taskQuery).singleResult();
        when(task.getBusinessKey()).thenReturn("4");
        when(task.getId()).thenReturn("task");
        when(applications.findByBusinessKey("4")).thenReturn(h);
        Employee manager = new Employee();
        manager.setEmail("manager@example.com");
        when(employees.queryDeptManagerByUserName(80)).thenReturn(manager);
        com.ada.approval.workflow.ApplicantWorkflow routing =
                mock(com.ada.approval.workflow.ApplicantWorkflow.class);
        ReflectionTestUtils.setField(service, "applicantWorkflow", routing);
        when(routing.variables(any(), eq(2)))
                .thenReturn(
                        new HashMap<>(
                                Map.of(
                                        "user",
                                        "user@example.com",
                                        "manager",
                                        "manager@example.com",
                                        "day",
                                        2,
                                        "applicantWorkflowIdentity",
                                        "EMPLOYEE")));
        RepositoryService definitions = mock(RepositoryService.class);
        var definitionQuery =
                mock(org.activiti.engine.repository.ProcessDefinitionQuery.class, RETURNS_SELF);
        var definition = mock(org.activiti.engine.repository.ProcessDefinition.class);
        when(definitions.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getId()).thenReturn("definition");
        org.activiti.bpmn.model.BpmnModel model = new org.activiti.bpmn.model.BpmnModel();
        var main = new org.activiti.bpmn.model.Process();
        main.setId("hr_employee_holiday");
        var gateway = new org.activiti.bpmn.model.ExclusiveGateway();
        gateway.setId("applicant_route");
        main.addFlowElement(gateway);
        model.addProcess(main);
        when(definitions.getBpmnModel("definition")).thenReturn(model);
        ReflectionTestUtils.setField(service, "processDefinitions", definitions);
        service.saveHolidayApply(h);
        assertEquals(Integer.valueOf(8), h.getAccountId());
        assertEquals("process", h.getProcessInstanceId());
        verify(applications, times(2)).saveAndFlush(h);
        verify(taskQuery).taskAssignee("user@example.com");
        verify(taskQuery).processInstanceId("process");
        verify(tasks)
                .complete(eq("task"), argThat(v -> "manager@example.com".equals(v.get("manager"))));
    }

    @Test
    void oldDeployedDefinitionBlocksNewSubmissionBeforeAnyWrite() {
        var routing = mock(com.ada.approval.workflow.ApplicantWorkflow.class);
        var definitions = mock(RepositoryService.class);
        var query = mock(org.activiti.engine.repository.ProcessDefinitionQuery.class, RETURNS_SELF);
        when(definitions.createProcessDefinitionQuery()).thenReturn(query);
        ReflectionTestUtils.setField(service, "applicantWorkflow", routing);
        ReflectionTestUtils.setField(service, "processDefinitions", definitions);
        when(routing.variables(any(), eq(2)))
                .thenReturn(Map.of("applicantWorkflowIdentity", "EMPLOYEE"));
        HolidayApply h = new HolidayApply();
        h.setTitle("Leave");
        h.setHolidayType(1);
        h.setReason("Rest");
        h.setTime("2026-01-01 - 2026-01-02");
        var error =
                assertThrows(
                        com.ada.approval.api.error.ApiException.class,
                        () -> service.saveHolidayApply(h));
        assertEquals(409, error.getStatus());
        verify(applications, never()).saveAndFlush(any());
    }
}
