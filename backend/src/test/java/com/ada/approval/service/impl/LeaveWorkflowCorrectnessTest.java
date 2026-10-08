package com.ada.approval.service.impl;

import com.ada.approval.exception.ParamException;
import com.ada.approval.entity.*;
import com.ada.approval.query.MyTaskQuery;
import com.ada.approval.repository.*;
import com.ada.approval.service.*;
import com.ada.approval.workflow.WorkflowConstant;
import org.activiti.engine.*;
import org.activiti.engine.task.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No context, datasource or engine: verifies application decisions and API interactions. */
class LeaveWorkflowCorrectnessTest {
    private final HolidayApplyRepository repository = mock(HolidayApplyRepository.class);
    private final HolidayApplyServiceImpl service = new HolidayApplyServiceImpl(repository);
    private final TaskService tasks = mock(TaskService.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
    private final Task task = mock(Task.class);
    private final IAccountService accounts = mock(IAccountService.class);
    private final IEmployeeService employees = mock(IEmployeeService.class);
    private final IHolidayApprovalService approvals = mock(IHolidayApprovalService.class);
    private HolidayApply application;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("approver", "unused"));
        ReflectionTestUtils.setField(service, "taskService", tasks);
        ReflectionTestUtils.setField(service, "runtimeService", runtime);
        ReflectionTestUtils.setField(service, "accountService", accounts);
        ReflectionTestUtils.setField(service, "employeeService", employees);
        ReflectionTestUtils.setField(service, "holidayApprovalService", approvals);
        application = new HolidayApply();
        application.setId(4);
        application.setProcessInstanceId("process");
        application.setStatus(1);
        application.setIsValid(1);
        application.setDays(3);
        when(repository.findById(4)).thenReturn(Optional.of(application));
        when(repository.findLockedById(4)).thenReturn(application);
        when(tasks.createTaskQuery()).thenReturn(query);
        doReturn(task).when(query).singleResult();
        when(task.getId()).thenReturn("task");
        when(task.getProcessInstanceId()).thenReturn("process");
        when(task.getTaskDefinitionKey()).thenReturn("manager_check");
        when(task.getAssignee()).thenReturn("approver");
        Account account = new Account();
        account.setId(8);
        account.setUserName("approver");
        when(accounts.findAccountByUserName("approver")).thenReturn(account);
        when(approvals.save(any())).thenReturn(true);
        Employee boss = new Employee();
        boss.setEmail("boss@example.com");
        when(employees.findBoss()).thenReturn(boss);
        Employee hr = new Employee();
        hr.setEmail("hr@example.com");
        when(employees.findAllHrs()).thenReturn(Collections.singletonList(hr));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private HolidayApproval audit() {
        ArgumentCaptor<HolidayApproval> capture = ArgumentCaptor.forClass(HolidayApproval.class);
        verify(approvals).save(capture.capture());
        return capture.getValue();
    }

    @Test
    void matchingProcessAndTaskAccepted() {
        service.validateTask(4, "task");
        verify(query).taskId("task");
    }

    @Test
    void mismatchedProcessRejectedBeforeWrites() {
        when(task.getProcessInstanceId()).thenReturn("other");
        assertThrows(ParamException.class, () -> service.processTask(4, "task", true, ""));
        verifyNoInteractions(approvals, runtime);
        verify(tasks, never()).complete(anyString(), anyMap());
    }

    @Test
    void nonexistentTaskRejected() {
        doReturn(null).when(query).singleResult();
        assertThrows(ParamException.class, () -> service.processTask(4, "missing", true, ""));
        verifyNoInteractions(approvals);
    }

    @Test
    void differentAssigneeRejected() {
        when(task.getAssignee()).thenReturn("someone-else");
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.processTask(4, "task", true, ""));
        verifyNoInteractions(approvals);
    }

    @Test
    void candidateOnOtherTaskDoesNotAuthorizeThisTask() {
        when(task.getAssignee()).thenReturn(null);
        when(query.count()).thenReturn(0L);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.processTask(4, "task", true, ""));
        verify(query, times(2)).taskId("task");
        verify(query).taskCandidateUser("approver");
        verify(tasks, never()).claim(anyString(), anyString());
        verifyNoInteractions(approvals);
    }

    @Test
    void specificCandidateCanClaimAndApproveHrTask() {
        when(task.getAssignee()).thenReturn(null);
        when(query.count()).thenReturn(1L);
        when(task.getTaskDefinitionKey()).thenReturn("hr_check");
        service.processTask(4, "task", true, "Approved");
        verify(tasks).claim("task", "approver");
        assertEquals(Integer.valueOf(4), application.getStatus());
        assertEquals(Integer.valueOf(5), audit().getResult());
    }

    @Test
    void threeDaysSkipsGeneralManagerAndCreatesCompleteAudit() {
        service.processTask(4, "task", true, "Fine");
        ArgumentCaptor<Map> variables = ArgumentCaptor.forClass(Map.class);
        verify(tasks).complete(eq("task"), variables.capture());
        assertEquals(3, variables.getValue().get("day"));
        assertFalse(variables.getValue().containsKey("boss"));
        verify(employees, never()).findBoss();
        assertEquals(Integer.valueOf(2), application.getStatus());
        HolidayApproval a = audit();
        assertEquals("task", a.getTaskId());
        assertEquals("process", a.getProcessInstanceId());
        assertEquals("manager_check", a.getTaskDefKey());
        assertEquals(Integer.valueOf(8), a.getUserId());
        assertEquals("approver", a.getUserName());
        assertEquals(Integer.valueOf(1), a.getResult());
        assertNotNull(a.getCreateTime());
        assertEquals("Fine", a.getRemark());
    }

    @Test
    void fourDaysSetsGeneralManagerRoutingVariable() {
        application.setDays(4);
        service.processTask(4, "task", true, "");
        verify(tasks)
                .complete(
                        eq("task"),
                        argThat(
                                v ->
                                        Integer.valueOf(4).equals(v.get("day"))
                                                && "boss@example.com".equals(v.get("boss"))));
    }

    @Test
    void generalManagerApprovalAdvancesToHr() {
        when(task.getTaskDefinitionKey()).thenReturn("boss_check");
        service.processTask(4, "task", true, "");
        assertEquals(Integer.valueOf(2), application.getStatus());
        assertEquals(Integer.valueOf(3), audit().getResult());
        verify(tasks).complete(eq("task"), anyMap());
    }

    @Test
    void hrApprovalCompletesApplication() {
        when(task.getTaskDefinitionKey()).thenReturn("hr_check");
        service.processTask(4, "task", true, "");
        assertEquals(Integer.valueOf(4), application.getStatus());
        assertEquals(Integer.valueOf(5), audit().getResult());
    }

    @Test
    void rejectionTerminatesWithoutCompletingOrSuspending() {
        service.processTask(4, "task", false, "No");
        assertEquals(Integer.valueOf(3), application.getStatus());
        assertEquals(Integer.valueOf(2), audit().getResult());
        verify(runtime).deleteProcessInstance("process", "Leave rejected: No");
        verify(tasks, never()).complete(anyString(), anyMap());
        verify(runtime, never()).suspendProcessInstanceById(anyString());
        verifyNoInteractions(employees);
    }

    @Test
    void auditFailurePreventsEngineCompletion() {
        when(approvals.save(any())).thenReturn(false);
        assertThrows(ParamException.class, () -> service.processTask(4, "task", true, ""));
        verify(tasks, never()).complete(anyString(), anyMap());
        verify(runtime, never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    void completedApplicationRejectsFurtherApproval() {
        application.setStatus(4);
        assertThrows(ParamException.class, () -> service.processTask(4, "task", true, ""));
        verifyNoInteractions(tasks, approvals);
    }

    @Test
    void unsupportedTaskRejected() {
        when(task.getTaskDefinitionKey()).thenReturn("fill_form");
        assertThrows(ParamException.class, () -> service.processTask(4, "task", true, ""));
        verifyNoInteractions(approvals);
    }

    @Test
    void declaresRollbackForAllExceptions() throws Exception {
        Transactional tx =
                HolidayApplyServiceImpl.class
                        .getMethod(
                                "processTask",
                                Integer.class,
                                String.class,
                                boolean.class,
                                String.class)
                        .getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertArrayEquals(new Class[] {Exception.class}, tx.rollbackFor());
    }

    @Test
    void taskListUsesAuthenticatedUserBatchLookupAndCorrectCount() {
        when(task.getTaskDefinitionKey()).thenReturn("hr_check");
        when(query.list()).thenReturn(Collections.singletonList(task));
        when(repository.findByProcessInstanceIdIn(Collections.singletonList("process")))
                .thenReturn(Collections.singletonList(application));
        MyTaskQuery input = new MyTaskQuery();
        input.setUserName("spoofed");
        input.setPage(1);
        input.setLimit(10);
        Map<String, Object> rows = service.queryMyTaskList(input);
        assertEquals(1L, rows.get("count"));
        assertEquals(1, ((List) rows.get("data")).size());
        verify(query).taskCandidateOrAssigned("approver");
        assertEquals(1, service.countMyTask());
    }

    @Test
    void approvalHistoryUsesProcessAndStageInsteadOfCurrentStaff() {
        HolidayApprovalRepository r = mock(HolidayApprovalRepository.class);
        HolidayApproval a = new HolidayApproval();
        when(r.findFirstByProcessInstanceIdAndTaskDefKeyOrderByCreateTimeDescIdDesc(
                        "process", "boss_check"))
                .thenReturn(a);
        HolidayApprovalServiceImpl s = new HolidayApprovalServiceImpl(r);
        assertSame(
                a,
                s.queryHolidayApprovalByprocIdAndUserNameAndTaskDefKey(
                        "process", "boss_check", Arrays.asList("changed-email")));
        assertNull(s.findLatestApproval(null, "boss_check"));
    }

    @Test
    void rejectionAtBossAndHrStagesTerminatesWithCorrectAuditDecision() {
        String[] keys = {"boss_check", "hr_check"};
        int[] results = {4, 6};
        for (int i = 0; i < keys.length; i++) {
            application.setStatus(2);
            when(task.getTaskDefinitionKey()).thenReturn(keys[i]);
            service.processTask(4, "task", false, "Rejected");
            assertEquals(Integer.valueOf(3), application.getStatus());
            assertEquals(Integer.valueOf(results[i]), application.getApprovalStatus());
        }
        verify(runtime, times(2)).deleteProcessInstance("process", "Leave rejected: Rejected");
        verify(tasks, never()).complete(anyString(), anyMap());
    }

    @Test
    void activeBpmnUsesMoreThanThreeDaysAndMatchingBossKey() throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (java.io.InputStream input =
                getClass().getResourceAsStream("/bpmn/hr_employee_holiday.bpmn")) {
            assertNotNull(input);
            org.w3c.dom.Document document = factory.newDocumentBuilder().parse(input);
            org.w3c.dom.NodeList expressions =
                    document.getElementsByTagNameNS(
                            "http://www.omg.org/spec/BPMN/20100524/MODEL", "conditionExpression");
            Set<String> conditions = new HashSet<>();
            for (int i = 0; i < expressions.getLength(); i++)
                conditions.add(expressions.item(i).getTextContent().trim());
            assertTrue(conditions.contains("${day>3}"));
            assertTrue(conditions.contains("${day<=3}"));
            org.w3c.dom.NodeList userTasks =
                    document.getElementsByTagNameNS(
                            "http://www.omg.org/spec/BPMN/20100524/MODEL", "userTask");
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < userTasks.getLength(); i++)
                keys.add(((org.w3c.dom.Element) userTasks.item(i)).getAttribute("id"));
            assertTrue(keys.contains(WorkflowConstant.EMPLOYEE_HOLIDAY_BOSS_TASK_DEF_KEY));
            assertFalse(keys.contains("boos_check"));
        }
    }

    @Test
    void applicantCannotApproveOwnAssignedTask() {
        application.setAccountId(8);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.processTask(4, "task", true, ""));
        verifyNoInteractions(approvals, runtime);
        verify(tasks, never()).complete(anyString(), anyMap());
    }

    @Test
    void hrApplicantCompletesAtGeneralManager() {
        when(task.getTaskDefinitionKey()).thenReturn("boss_check");
        when(runtime.getVariable("process", "applicantWorkflowIdentity")).thenReturn("HR");
        service.processTask(4, "task", true, "");
        assertEquals(4, application.getStatus());
        assertEquals(3, audit().getResult());
    }

    @Test
    void newEmployeeRouteUsesSubmissionTimeApprovers() {
        when(runtime.getVariable("process", "applicantWorkflowIdentity")).thenReturn("EMPLOYEE");
        service.processTask(4, "task", true, "");
        verifyNoInteractions(employees);
        verify(tasks).complete(eq("task"), eq(Collections.emptyMap()));
    }

    @Test
    void applicantCannotApproveOwnCandidateTask() {
        application.setAccountId(8);
        when(task.getAssignee()).thenReturn(null);
        when(query.count()).thenReturn(1L);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.processTask(4, "task", false, ""));
        verify(tasks, never()).claim(anyString(), anyString());
        verifyNoInteractions(approvals, runtime);
    }
}
