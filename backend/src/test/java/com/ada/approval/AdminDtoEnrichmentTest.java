package com.ada.approval;

import com.ada.approval.api.service.AdminApiService;
import com.ada.approval.api.dto.ApiMapper;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminDtoEnrichmentTest {
    AdminApiService service;
    EmployeeRepository employees;
    DeptRepository departments;
    TitleCategoryRepository titles;
    EmployeeStatusRepository statuses;
    AccountRepository accounts;
    AccountRoleRepository assignments;
    RoleRepository roles;

    @BeforeEach
    void setup() {
        service = new AdminApiService();
        employees = mock(EmployeeRepository.class);
        departments = mock(DeptRepository.class);
        titles = mock(TitleCategoryRepository.class);
        statuses = mock(EmployeeStatusRepository.class);
        accounts = mock(AccountRepository.class);
        assignments = mock(AccountRoleRepository.class);
        roles = mock(RoleRepository.class);
        ReflectionTestUtils.setField(service, "employees", employees);
        ReflectionTestUtils.setField(service, "departments", departments);
        ReflectionTestUtils.setField(service, "titles", titles);
        ReflectionTestUtils.setField(service, "statuses", statuses);
        ReflectionTestUtils.setField(service, "accounts", accounts);
        ReflectionTestUtils.setField(service, "assignments", assignments);
        ReflectionTestUtils.setField(service, "roles", roles);
    }

    @Test
    void employeeReferencesAreBatchedAndIdsPreserved() {
        Employee e = new Employee();
        e.setId(9);
        e.setDeptId(2);
        e.setTitleCategoryId(3);
        e.setEmployStatusId(4);
        Dept d = new Dept();
        d.setId(2);
        d.setDeptName("Operations");
        TitleCategory t = new TitleCategory();
        t.setId(3);
        t.setTitleName("\u603b\u7ecf\u7406");
        EmployeeStatus status = new EmployeeStatus();
        status.setId(4);
        status.setName("Active");
        when(employees.findByStatusAndEmpNameContainingIgnoreCase(eq(1), eq(""), any()))
                .thenReturn(new PageImpl<>(Arrays.asList(e, e)));
        when(departments.findAllById(any())).thenReturn(Collections.singletonList(d));
        when(titles.findAllById(any())).thenReturn(Collections.singletonList(t));
        when(statuses.findAllById(any())).thenReturn(Collections.singletonList(status));
        var result = service.employees(0, 20, null);
        assertEquals(2, result.getContent().size());
        var dto = result.getContent().get(0);
        assertEquals(9, dto.getId());
        assertEquals(2, dto.getDepartmentId());
        assertEquals("Operations", dto.getDepartmentName());
        assertEquals("General Manager", dto.getJobTitleName());
        assertEquals("Active", dto.getEmployeeStatusName());
        verify(departments, times(1)).findAllById(Collections.singleton(2));
        verify(titles, times(1)).findAllById(Collections.singleton(3));
        verify(statuses, times(1)).findAllById(Collections.singleton(4));
    }

    @Test
    void departmentsResolveManagersAndRootWithoutNumericLabels() {
        Dept d = new Dept();
        d.setId(1);
        d.setManagerId(7);
        d.setParentId(0);
        Employee e = new Employee();
        e.setId(7);
        e.setEmpName("Manager");
        when(departments.findByStatusAndDeptNameContainingIgnoreCase(eq(1), eq(""), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(d)));
        when(employees.findAllById(any())).thenReturn(Collections.singletonList(e));
        when(departments.findAllById(any())).thenReturn(Collections.emptyList());
        var dto = service.departments(0, 20, "").getContent().get(0);
        assertEquals("Manager", dto.getManagerName());
        assertNull(dto.getParentDepartmentName());
        assertEquals(7, dto.getManagerEmployeeId());
    }

    @Test
    void assignmentUsesScalarDisplayProjectionAndPreservesAccountIdentity() throws Exception {
        Role r = new Role();
        r.setId(2);
        r.setStatus(1);
        when(roles.findById(2)).thenReturn(Optional.of(r));
        AccountRole a = new AccountRole();
        a.setId(5);
        a.setRoleId(2);
        a.setAccountId(11);
        when(assignments.findByRoleId(eq(2), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(a)));
        when(accounts.findAccountDisplayRows(any()))
                .thenReturn(
                        Collections.singletonList(
                                new Object[] {11, "Employee A", "a@example.test"}));
        var dto = service.assignments(2, 0, 20).getContent().get(0);
        assertEquals(11, dto.getAccountId());
        assertEquals("Employee A", dto.getAccountDisplayName());
        assertEquals("a@example.test", dto.getAccountEmail());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto);
        assertFalse(json.toLowerCase().contains("password"));
        verify(accounts).findAccountDisplayRows(Collections.singleton(11));
        verifyNoMoreInteractions(accounts);
    }

    @Test
    void displayAliasDoesNotMutateWorkflowIdentity() {
        TitleCategory t = new TitleCategory();
        t.setTitleName("\u4eba\u4e8b");
        var dto = ApiMapper.title(t);
        assertEquals("HR", dto.getDisplayName());
        assertEquals("HR", dto.getName());
        assertEquals("\u4eba\u4e8b", t.getTitleName());
        assertEquals("Employee", ApiMapper.displayJobTitle("Employee"));
        assertNull(ApiMapper.displayJobTitle(null));
    }

    private com.ada.approval.api.dto.ApiDtos.CreateEmployeeRequest employeeRequest() {
        var dto = new com.ada.approval.api.dto.ApiDtos.CreateEmployeeRequest();
        dto.setName("Employee C");
        dto.setEmail("c@example.test");
        dto.setMobile("555");
        return dto;
    }

    @Test
    void employeeCreateAssignsExistingActiveAndRetainsProfileAndPassword() {
        var business = mock(com.ada.approval.service.IEmployeeService.class);
        ReflectionTestUtils.setField(service, "employeeService", business);
        EmployeeStatus active = new EmployeeStatus();
        active.setId(42);
        active.setName("Active");
        active.setStatus(1);
        when(statuses.findAllByNameIgnoreCaseAndStatus("Active", 1))
                .thenReturn(Collections.singletonList(active));
        var dto = employeeRequest();
        dto.setDepartmentId(2);
        dto.setJobTitleId(3);
        Dept dept = new Dept();
        dept.setId(2);
        dept.setStatus(1);
        when(departments.findById(2)).thenReturn(Optional.of(dept));
        TitleCategory title = new TitleCategory();
        title.setId(3);
        title.setStatus(1);
        when(titles.findById(3)).thenReturn(Optional.of(title));
        service.createEmployee(dto);
        verify(business)
                .saveEmployee(
                        argThat(
                                e ->
                                        Integer.valueOf(42).equals(e.getEmployStatusId())
                                                && Integer.valueOf(2).equals(e.getDeptId())
                                                && Integer.valueOf(3).equals(e.getTitleCategoryId())
                                                && "c@example.test".equals(e.getEmail())),
                        argThat(password -> password != null && password.length() == 24));
        verify(statuses, never()).save(any());
    }

    @Test
    void missingOrAmbiguousActiveFailsBeforeEmployeeWrites() {
        var business = mock(com.ada.approval.service.IEmployeeService.class);
        ReflectionTestUtils.setField(service, "employeeService", business);
        when(statuses.findAllByNameIgnoreCaseAndStatus("Active", 1))
                .thenReturn(Collections.emptyList());
        var error =
                assertThrows(
                        com.ada.approval.api.error.ApiException.class,
                        () -> service.createEmployee(employeeRequest()));
        assertEquals(500, error.getStatus());
        assertEquals(
                "Active employee status is unavailable. Please contact an administrator.",
                error.getMessage());
        EmployeeStatus active = new EmployeeStatus();
        active.setId(42);
        when(statuses.findAllByNameIgnoreCaseAndStatus("Active", 1))
                .thenReturn(Arrays.asList(active, active));
        assertThrows(
                com.ada.approval.api.error.ApiException.class,
                () -> service.createEmployee(employeeRequest()));
        verifyNoInteractions(business);
    }

    @Test
    void profileUpdatePreservesExistingStatusIncludingNull() {
        var business = mock(com.ada.approval.service.IEmployeeService.class);
        ReflectionTestUtils.setField(service, "employeeService", business);
        Employee existing = new Employee();
        existing.setId(9);
        existing.setEmployStatusId(77);
        when(employees.findByIdAndStatus(9, 1)).thenReturn(existing);
        service.updateEmployee(9, employeeRequest());
        verify(business)
                .updateEmployee(argThat(e -> Integer.valueOf(77).equals(e.getEmployStatusId())));
        reset(business);
        existing.setEmployStatusId(null);
        service.updateEmployee(9, employeeRequest());
        verify(business).updateEmployee(argThat(e -> e.getEmployStatusId() == null));
        verify(statuses, never()).findAllByNameIgnoreCaseAndStatus(anyString(), anyInt());
    }

    @Test
    void suppliedStatusIsNotPartOfCreateOrUpdateContract() throws Exception {
        var json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .disable(
                                com.fasterxml.jackson.databind.DeserializationFeature
                                        .FAIL_ON_UNKNOWN_PROPERTIES);
        var dto =
                json.readValue(
                        "{\"name\":\"Employee C\",\"employeeStatusId\":999}",
                        com.ada.approval.api.dto.ApiDtos.CreateEmployeeRequest.class);
        assertNull(ApiMapper.employee(dto).getEmployStatusId());
        assertFalse(json.writeValueAsString(dto).contains("employeeStatusId"));
        assertThrows(
                NoSuchFieldException.class,
                () ->
                        com.ada.approval.api.dto.ApiDtos.EmployeeRequest.class.getDeclaredField(
                                "employeeStatusId"));
    }
}
