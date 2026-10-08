package com.ada.approval.api;

import com.ada.approval.api.service.LeaveWithdrawalService;
import com.ada.approval.api.dto.ApiMapper;
import com.ada.approval.api.error.ApiException;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.IAccountService;
import org.activiti.engine.*;
import org.activiti.engine.task.*;
import org.activiti.engine.runtime.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.interceptor.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

class LeaveWithdrawalTest {
    final HolidayApplyRepository requests = mock(HolidayApplyRepository.class);
    final HolidayApprovalRepository audits = mock(HolidayApprovalRepository.class);
    final RuntimeService runtime = mock(RuntimeService.class);
    final TaskService tasks = mock(TaskService.class);
    final IAccountService accounts = mock(IAccountService.class);
    final ProcessInstanceQuery processes = mock(ProcessInstanceQuery.class, RETURNS_SELF);
    final TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
    final Task task = mock(Task.class);
    final LeaveWithdrawalService service =
            new LeaveWithdrawalService(requests, audits, runtime, tasks, accounts);
    HolidayApply request;
    Account applicant;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("applicant", "unused"));
        applicant = new Account();
        applicant.setId(11);
        applicant.setStatus(1);
        applicant.setUserName("applicant");
        request = new HolidayApply();
        request.setId(4);
        request.setAccountId(11);
        request.setIsValid(1);
        request.setStatus(2);
        request.setProcessInstanceId("process");
        when(accounts.findAccountByUserName("applicant")).thenReturn(applicant);
        when(requests.findLockedById(4)).thenReturn(request);
        when(runtime.createProcessInstanceQuery()).thenReturn(processes);
        when(processes.singleResult()).thenReturn(mock(ProcessInstance.class));
        when(tasks.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(task.getId()).thenReturn("task");
        when(task.getProcessInstanceId()).thenReturn("process");
        when(task.getTaskDefinitionKey()).thenReturn("manager_check");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
        "Employee short,manager_check",
        "Employee after manager,hr_check",
        "Employee long,boss_check",
        "Manager applicant,boss_check",
        "General Manager applicant,hr_check",
        "HR applicant,boss_check"
    })
    void ownerCanWithdrawAtEverySupportedStage(String label, String key) {
        when(task.getTaskDefinitionKey()).thenReturn(key);
        service.withdraw(4);
        assertEquals(5, request.getStatus());
        assertEquals(7, request.getApprovalStatus());
        assertEquals(1, request.getIsValid());
        var capture = org.mockito.ArgumentCaptor.forClass(HolidayApproval.class);
        verify(audits).saveAndFlush(capture.capture());
        var audit = capture.getValue();
        assertEquals(7, audit.getResult());
        assertEquals(11, audit.getUserId());
        assertEquals("applicant", audit.getUserName());
        assertNotNull(audit.getCreateTime());
        assertEquals("process", audit.getProcessInstanceId());
        assertEquals("task", audit.getTaskId());
        assertEquals(key, audit.getTaskDefKey());
        assertEquals("WITHDRAWN", ApiMapper.approval(audit).getDecision());
        assertEquals("WITHDRAWN", ApiMapper.leave(request).getStatus());
        verify(runtime).deleteProcessInstance("process", "Leave withdrawn by applicant");
        verify(requests).saveAndFlush(request);
        verifyNoMoreInteractions(audits);
        verify(tasks, never()).complete(anyString());
        verify(requests, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"another employee", "system admin"})
    void nonOwnerCannotWithdraw(String identity) {
        applicant.setId(16);
        assertThrows(AccessDeniedException.class, () -> service.withdraw(4));
        verifyNoInteractions(runtime, tasks, audits);
        verify(requests, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void terminalRequestsConflict(int status) {
        request.setStatus(status);
        assertEquals(409, assertThrows(ApiException.class, () -> service.withdraw(4)).getStatus());
        verifyNoInteractions(runtime, tasks, audits);
    }

    @Test
    void missingRequestReturns404() {
        when(requests.findLockedById(4)).thenReturn(null);
        assertEquals(404, assertThrows(ApiException.class, () -> service.withdraw(4)).getStatus());
        verifyNoInteractions(audits, runtime);
    }

    @Test
    void noRuntimeProcessConflicts() {
        when(processes.singleResult()).thenReturn(null);
        assertEquals(409, assertThrows(ApiException.class, () -> service.withdraw(4)).getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void multipleTasksConflict() {
        when(taskQuery.list()).thenReturn(List.of(task, task));
        assertEquals(409, assertThrows(ApiException.class, () -> service.withdraw(4)).getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void taskProcessMismatchConflicts() {
        when(task.getProcessInstanceId()).thenReturn("other");
        assertEquals(409, assertThrows(ApiException.class, () -> service.withdraw(4)).getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void unknownTaskConflicts() {
        when(task.getTaskDefinitionKey()).thenReturn("fill");
        assertEquals(409, assertThrows(ApiException.class, () -> service.withdraw(4)).getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void capabilityRequiresOwnerAndAuthoritativeActiveState() {
        assertTrue(service.canWithdraw(request, applicant));
        applicant.setId(12);
        assertFalse(service.canWithdraw(request, applicant));
        applicant.setId(11);
        when(processes.singleResult()).thenReturn(null);
        assertFalse(service.canWithdraw(request, applicant));
    }

    LeaveWithdrawalService transactional(PlatformTransactionManager manager) {
        var proxy = new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(
                new TransactionInterceptor(
                        manager,
                        new org.springframework.transaction.annotation
                                .AnnotationTransactionAttributeSource()));
        return (LeaveWithdrawalService) proxy.getProxy();
    }

    @Test
    void terminationFailureRequestsRollbackOfWholeOperation() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("engine failure"))
                .when(runtime)
                .deleteProcessInstance(anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> transactional(manager).withdraw(4));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }

    @Test
    void auditFailureRollsBackWithoutTerminatingProcess() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        when(audits.saveAndFlush(any())).thenThrow(new IllegalStateException("audit failure"));
        assertThrows(IllegalStateException.class, () -> transactional(manager).withdraw(4));
        verify(manager).rollback(status);
        verify(runtime, never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    void requestPersistenceFailureRollsBackAuditAndDoesNotTerminate() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        when(requests.saveAndFlush(any())).thenThrow(new IllegalStateException("request failure"));
        assertThrows(IllegalStateException.class, () -> transactional(manager).withdraw(4));
        verify(manager).rollback(status);
        verify(runtime, never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    void bothActionsUseSamePessimisticRequestLock() throws Exception {
        var lock =
                HolidayApplyRepository.class
                        .getMethod("findLockedById", Integer.class)
                        .getAnnotation(org.springframework.data.jpa.repository.Lock.class);
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, lock.value());
        var tx =
                LeaveWithdrawalService.class
                        .getMethod("withdraw", Integer.class)
                        .getAnnotation(
                                org.springframework.transaction.annotation.Transactional.class);
        assertArrayEquals(new Class[] {Exception.class}, tx.rollbackFor());
    }
}
