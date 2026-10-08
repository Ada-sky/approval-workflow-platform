package com.ada.approval.service.impl;

import com.ada.approval.exception.ParamException;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.IAccountService;
import com.ada.approval.service.IPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Plain Mockito tests: no Spring context, datasource, or Activiti engine. */
class NonWorkflowBugFixTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final IAccountService accountService = mock(IAccountService.class);

    private Account account() {
        Account a = new Account();
        a.setId(70);
        a.setEmpId(7);
        a.setUserName("old@example.com");
        return a;
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(7);
        e.setEmpName("Employee");
        e.setMobile("123");
        e.setEmail("new@example.com");
        e.setStatus(1);
        return e;
    }

    private EmployeeServiceImpl employeeService() {
        EmployeeServiceImpl s = spy(new EmployeeServiceImpl(employees, accounts));
        ReflectionTestUtils.setField(s, "accountService", accountService);
        when(employees.findByIdAndStatus(7, 1)).thenReturn(employee());
        doReturn(true).when(s).updateById(any(Employee.class));
        return s;
    }

    @Test
    void assignmentUsesAccountIdInsteadOfEmployeeId() {
        AccountRoleRepository r = mock(AccountRoleRepository.class);
        AccountRoleServiceImpl s = new AccountRoleServiceImpl(r);
        ReflectionTestUtils.setField(s, "accountService", accountService);
        when(accountService.findAccountByEmpId(7)).thenReturn(account());
        s.saveAccountRole(3, 7);
        verify(r)
                .saveAndFlush(
                        argThat(ar -> ar.getAccountId().equals(70) && ar.getRoleId().equals(3)));
    }

    @Test
    void assignmentRejectsMissingAccount() {
        AccountRoleRepository r = mock(AccountRoleRepository.class);
        AccountRoleServiceImpl s = new AccountRoleServiceImpl(r);
        ReflectionTestUtils.setField(s, "accountService", accountService);
        assertThrows(ParamException.class, () -> s.saveAccountRole(3, 7));
        verify(r, never()).saveAndFlush(any());
    }

    @Test
    void changedEmailUpdatesAccountWithoutTimestamp() {
        EmployeeServiceImpl s = employeeService();
        Account a = account();
        when(accountService.findAccountByEmpId(7)).thenReturn(a);
        when(accountService.updateById(a)).thenReturn(true);
        s.updateEmployee(employee());
        assertEquals("new@example.com", a.getUsername());
        verify(accounts).existsByUserNameAndIdNot("new@example.com", 70);
        verify(accountService).updateById(a);
    }

    @Test
    void unchangedEmailDoesNotUpdateAccount() {
        EmployeeServiceImpl s = employeeService();
        Employee e = employee();
        e.setEmail("old@example.com");
        when(accountService.findAccountByEmpId(7)).thenReturn(account());
        s.updateEmployee(e);
        verify(accountService, never()).updateById(any());
        verifyNoInteractions(accounts);
    }

    @Test
    void duplicateUsernameRejectsUpdateBeforeWrites() {
        EmployeeServiceImpl s = employeeService();
        when(accountService.findAccountByEmpId(7)).thenReturn(account());
        when(accounts.existsByUserNameAndIdNot("new@example.com", 70)).thenReturn(true);
        assertThrows(ParamException.class, () -> s.updateEmployee(employee()));
        verify(s, never()).updateById(any());
        verify(accountService, never()).updateById(any());
    }

    @Test
    void duplicateEmployeeEmailStillRejected() {
        EmployeeServiceImpl s = employeeService();
        Employee other = employee();
        other.setId(8);
        when(employees.findByEmailAndStatus("new@example.com", 1)).thenReturn(other);
        assertThrows(ParamException.class, () -> s.updateEmployee(employee()));
        verify(s, never()).updateById(any());
    }

    @Test
    void updateRejectsMissingAccountBeforeWrites() {
        EmployeeServiceImpl s = employeeService();
        assertThrows(ParamException.class, () -> s.updateEmployee(employee()));
        verify(s, never()).updateById(any());
    }

    @Test
    void deletionUsesEmployeeIdAndSoftDeletesBoth() {
        EmployeeServiceImpl s = employeeService();
        Account a = account();
        when(accountService.findAccountByEmpId(7)).thenReturn(a);
        when(accountService.updateById(a)).thenReturn(true);
        s.deleteEmployee(7);
        verify(s).updateById(argThat(e -> e.getStatus().equals(0)));
        assertEquals(Integer.valueOf(0), a.getStatus());
        verify(accountService).updateById(a);
        verify(accountService, never()).findAccountByUserName(anyString());
    }

    @Test
    void deletionRejectsMissingAccountBeforeWrites() {
        EmployeeServiceImpl s = employeeService();
        assertThrows(ParamException.class, () -> s.deleteEmployee(7));
        verify(s, never()).updateById(any());
    }

    private TitleCategory category() {
        TitleCategory t = new TitleCategory();
        t.setId(5);
        t.setTitleName("Developer");
        t.setTitleNum("DEV");
        t.setLevel(1);
        return t;
    }

    @Test
    void titleCreationRejectsDuplicateRootWithNullParent() {
        TitleCategoryRepository r = mock(TitleCategoryRepository.class);
        TitleCategoryServiceImpl s = new TitleCategoryServiceImpl(r);
        when(r.countSiblingName("Developer", 1, null, null)).thenReturn(1L);
        assertThrows(ParamException.class, () -> s.savetTitleCategory(category()));
        verify(r, never()).saveAndFlush(any());
    }

    @Test
    void sameTitleNameUnderAnotherParentAllowed() {
        TitleCategoryRepository r = mock(TitleCategoryRepository.class);
        TitleCategoryServiceImpl s = new TitleCategoryServiceImpl(r);
        TitleCategory t = category();
        t.setParentId(9);
        s.savetTitleCategory(t);
        verify(r).countSiblingName("Developer", 1, 9, null);
        verify(r).saveAndFlush(t);
    }

    @Test
    void titleUpdateExcludesSelfAndPreservesOmittedHierarchy() {
        TitleCategoryRepository r = mock(TitleCategoryRepository.class);
        TitleCategoryServiceImpl s = spy(new TitleCategoryServiceImpl(r));
        TitleCategory existing = category();
        existing.setParentId(9);
        when(r.findById(5)).thenReturn(Optional.of(existing));
        when(r.findByTitleNumAndStatus("DEV", 1)).thenReturn(existing);
        doReturn(true).when(s).updateById(any());
        TitleCategory submitted = category();
        submitted.setLevel(null);
        s.updateTitleCategory(submitted);
        verify(r).countSiblingName("Developer", 1, 9, 5);
        verify(s).updateById(submitted);
    }

    @Test
    void titleUpdateRejectsOtherSibling() {
        TitleCategoryRepository r = mock(TitleCategoryRepository.class);
        TitleCategoryServiceImpl s = spy(new TitleCategoryServiceImpl(r));
        when(r.findById(5)).thenReturn(Optional.of(category()));
        when(r.countSiblingName("Developer", 1, null, 5)).thenReturn(1L);
        assertThrows(ParamException.class, () -> s.updateTitleCategory(category()));
        verify(s, never()).updateById(any());
    }

    private MenuServiceImpl menuService(MenuRepository r, IPermissionService grants) {
        MenuServiceImpl s = spy(new MenuServiceImpl(r));
        ReflectionTestUtils.setField(s, "permissionService", grants);
        Menu m = new Menu();
        m.setId(4);
        m.setIsValid(1);
        when(r.findById(4)).thenReturn(Optional.of(m));
        doReturn(true).when(s).updateById(any());
        return s;
    }

    @Test
    void menuWithActiveChildrenCannotBeDeleted() {
        MenuRepository r = mock(MenuRepository.class);
        IPermissionService grants = mock(IPermissionService.class);
        MenuServiceImpl s = menuService(r, grants);
        when(r.countByParentIdAndIsValid(4, 1)).thenReturn(1L);
        assertThrows(ParamException.class, () -> s.deleteMenu(4));
        verifyNoInteractions(grants);
        verify(s, never()).updateById(any());
    }

    @Test
    void leafWithoutGrantsCanBeSoftDeleted() {
        MenuRepository r = mock(MenuRepository.class);
        IPermissionService grants = mock(IPermissionService.class);
        MenuServiceImpl s = menuService(r, grants);
        s.deleteMenu(4);
        verify(grants).removeByMenuId(4);
        verify(s).updateById(argThat(m -> m.getIsValid().equals(0)));
    }

    @Test
    void grantRemovalFailurePreventsMenuUpdate() {
        MenuRepository r = mock(MenuRepository.class);
        IPermissionService grants = mock(IPermissionService.class);
        MenuServiceImpl s = menuService(r, grants);
        when(grants.removeByMenuId(4)).thenThrow(new IllegalStateException("delete failed"));
        assertThrows(IllegalStateException.class, () -> s.deleteMenu(4));
        verify(s, never()).updateById(any());
    }
}
