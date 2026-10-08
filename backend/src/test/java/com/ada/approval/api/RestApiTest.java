package com.ada.approval.api;

import com.ada.approval.api.controller.*;
import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.error.*;
import com.ada.approval.api.service.*;
import com.ada.approval.config.security.*;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Explicit MVC/filter fixture only: mocked repositories/workflow, no Boot context, database or
 * engine.
 */
class RestApiTest {
    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({
        LeaveTypeAdminApiController.class,
        LeaveWithdrawalApiController.class,
        SecurityConfig.class,
        AuthApiController.class,
        LeaveRequestApiController.class,
        ApprovalApiController.class,
        ApprovalHistoryApiController.class,
        EmployeeApiController.class,
        DepartmentApiController.class,
        RoleApiController.class,
        MenuApiController.class,
        EmployeeStatusApiController.class,
        JobTitleApiController.class,
        ReferenceApiController.class,
        ApiExceptionHandler.class,
        com.ada.approval.api.error.GlobalApiExceptionHandler.class
    })
    static class Fixture {
        @Bean
        static org.springframework.validation.beanvalidation.MethodValidationPostProcessor
                methodValidation() {
            return new org.springframework.validation.beanvalidation
                    .MethodValidationPostProcessor();
        }

        @Bean
        IAccountService accounts() {
            return mock(IAccountService.class);
        }

        @Bean
        IPermissionService permissions() {
            return mock(IPermissionService.class);
        }

        @Bean
        PermissionRepository grants() {
            return mock(PermissionRepository.class);
        }

        @Bean
        BackendAuthorization backendAuthorization(PermissionRepository grants) {
            return new BackendAuthorization(grants);
        }

        @Bean
        HpAuthenticationSuccessHandler success() {
            return new HpAuthenticationSuccessHandler();
        }

        @Bean
        HpAuthenticationFailureHandler failure() {
            return new HpAuthenticationFailureHandler();
        }

        @Bean
        com.ada.approval.workflow.ApplicantWorkflow applicantWorkflow() {
            var bean = mock(com.ada.approval.workflow.ApplicantWorkflow.class);
            when(bean.canApply(any())).thenReturn(true);
            return bean;
        }

        @Bean
        LeaveWithdrawalService withdrawals() {
            return mock(LeaveWithdrawalService.class);
        }

        @Bean
        WorkflowProgressService progress() {
            return mock(WorkflowProgressService.class);
        }

        @Bean
        LeaveApiService leave() {
            return new LeaveApiService();
        }

        @Bean
        IHolidayApplyService workflow() {
            return mock(IHolidayApplyService.class);
        }

        @Bean
        EmployeeRepository employees() {
            return mock(EmployeeRepository.class);
        }

        @Bean
        AccountRepository accountRepository() {
            return mock(AccountRepository.class);
        }

        @Bean
        HolidayApplyRepository requests() {
            return mock(HolidayApplyRepository.class);
        }

        @Bean
        HolidayApprovalRepository approvals() {
            return mock(HolidayApprovalRepository.class);
        }

        @Bean
        HolidayTypeRepository types() {
            return mock(HolidayTypeRepository.class);
        }

        @Bean
        org.activiti.engine.TaskService tasks() {
            return mock(org.activiti.engine.TaskService.class);
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private IAccountService accounts;
    private IPermissionService permissions;
    private PermissionRepository grants;
    private AdminApiService admin;
    private IHolidayApplyService workflow;
    private final String validLeave =
            "{\"title\":\"Annual leave\",\"leaveTypeId\":1,\"reason\":\"Family time\",\"startDate\":\"2026-11-01T09:00:00\",\"endDate\":\"2026-11-02T09:00:00\"}";

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        admin = mock(AdminApiService.class);
        context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("admin", admin));
        context.register(Fixture.class);
        context.refresh();
        mvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .addFilters(context.getBean(FilterChainProxy.class))
                        .build();
        accounts = context.getBean(IAccountService.class);
        permissions = context.getBean(IPermissionService.class);
        grants = context.getBean(PermissionRepository.class);
        admin = context.getBean(AdminApiService.class);
        workflow = context.getBean(IHolidayApplyService.class);
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private MockHttpSession session(String... codes) {
        Account a = new Account();
        a.setId(8);
        a.setEmpId(80);
        a.setStatus(1);
        a.setUserName("employee");
        a.setPassword("hash");
        a.setGrantedAuthority(AuthorityUtils.createAuthorityList(codes));
        when(accounts.findAccountByUserName("employee")).thenReturn(a);
        when(permissions.findAuthorityByUserName("employee")).thenReturn(Arrays.asList(codes));
        SecurityContext security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(
                new UsernamePasswordAuthenticationToken(a, null, a.getAuthorities()));
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return s;
    }

    private String csrf(MockHttpSession s) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setSession(s);
        HttpSessionCsrfTokenRepository repo = new HttpSessionCsrfTokenRepository();
        CsrfToken t = repo.generateToken(r);
        repo.saveToken(t, r, new MockHttpServletResponse());
        new XorCsrfTokenRequestAttributeHandler().handle(r, new MockHttpServletResponse(), () -> t);
        return ((CsrfToken) r.getAttribute(CsrfToken.class.getName())).getToken();
    }

    private HolidayApply leave() {
        HolidayApply h = new HolidayApply();
        h.setId(4);
        h.setAccountId(8);
        h.setStatus(4);
        h.setIsValid(1);
        h.setProcessInstanceId("process");
        return h;
    }

    @Test
    void unauthenticatedApiReturnsJson401WithoutRedirect() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void anonymousClientCanObtainCsrfToken() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void reactCsrfTokenWorksThroughLoginSessionAndPostLogout() throws Exception {
        Account account = new Account();
        account.setId(8);
        account.setStatus(1);
        account.setUserName("employee");
        account.setPassword(
                context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
                        .encode("migration-unit-password"));
        when(accounts.findAccountByUserName("employee")).thenReturn(account);
        when(permissions.findAuthorityByUserName("employee"))
                .thenReturn(java.util.Collections.emptyList());
        MvcResult tokenResult =
                mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        com.fasterxml.jackson.databind.ObjectMapper json =
                new com.fasterxml.jackson.databind.ObjectMapper();
        String token =
                json.readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(
                        post("/login")
                                .session(session)
                                .header("X-CSRF-TOKEN", token)
                                .param("userName", "employee")
                                .param("password", "migration-unit-password"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("employee"));
        MvcResult renewed =
                mvc.perform(get("/api/auth/csrf").session(session))
                        .andExpect(status().isOk())
                        .andReturn();
        String next =
                json.readTree(renewed.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", next))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotAccessAdminApi() throws Exception {
        mvc.perform(get("/api/employees").session(session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(admin);
    }

    @Test
    void currentUserReturnsDtoWithoutPassword() throws Exception {
        mvc.perform(get("/api/auth/me").session(session("100214")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("employee"))
                .andExpect(jsonPath("$.displayName").value("employee"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.authorities[0]").value("100214"));
    }

    @Test
    void currentUserUsesLinkedEmployeeName() throws Exception {
        Employee employee = new Employee();
        employee.setEmpName("Employee A");
        when(context.getBean(EmployeeRepository.class).findById(80))
                .thenReturn(Optional.of(employee));
        mvc.perform(get("/api/auth/me").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Employee A"))
                .andExpect(jsonPath("$.username").value("employee"));
    }

    @Test
    void currentUserWithoutLinkedEmployeeUsesUsernameAndSkipsLookup() throws Exception {
        MockHttpSession session = session();
        accounts.findAccountByUserName("employee").setEmpId(null);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("employee"));
        verifyNoInteractions(context.getBean(EmployeeRepository.class));
    }

    @Test
    void currentUserWithBlankEmployeeNameUsesUsername() throws Exception {
        Employee employee = new Employee();
        employee.setEmpName(" ");
        when(context.getBean(EmployeeRepository.class).findById(80))
                .thenReturn(Optional.of(employee));
        mvc.perform(get("/api/auth/me").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("employee"));
    }

    @Test
    void disabledApiSessionReturnsJson401() throws Exception {
        MockHttpSession s = session();
        when(accounts.findAccountByUserName("employee")).thenReturn(null);
        mvc.perform(get("/api/auth/me").session(s))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session no longer valid"));
    }

    @Test
    void missingCsrfReturnsJson403() throws Exception {
        mvc.perform(
                        post("/api/leave-requests")
                                .session(session())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validLeave))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(workflow);
    }

    @Test
    void validCreateReturns201AndIgnoresProtectedFields() throws Exception {
        MockHttpSession s = session();
        HolidayType type = new HolidayType();
        type.setId(1);
        when(context.getBean(HolidayTypeRepository.class).findById(1))
                .thenReturn(Optional.of(type));
        doAnswer(
                        invocation -> {
                            HolidayApply h = invocation.getArgument(0);
                            assertNull(h.getId());
                            assertNull(h.getAccountId());
                            assertNull(h.getStatus());
                            assertNull(h.getProcessInstanceId());
                            h.setId(4);
                            h.setStatus(1);
                            h.setAccountId(8);
                            return null;
                        })
                .when(workflow)
                .saveHolidayApply(any());
        String json =
                validLeave.substring(0, validLeave.length() - 1)
                        + ",\"id\":999,\"accountId\":999,\"status\":4,\"processInstanceId\":\"forged\",\"isValid\":0}";
        mvc.perform(
                        post("/api/leave-requests")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/leave-requests/4"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.accountId").doesNotExist());
    }

    @Test
    void missingRequiredDtoFieldsReturnEnglish400() throws Exception {
        MockHttpSession s = session();
        mvc.perform(
                        post("/api/leave-requests")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(workflow);
    }

    @Test
    void sameDayMidnightDatesAreAcceptedAndForwardedWithoutTimezoneConversion() throws Exception {
        MockHttpSession s = session();
        HolidayType type = new HolidayType();
        type.setId(1);
        when(context.getBean(HolidayTypeRepository.class).findById(1))
                .thenReturn(Optional.of(type));
        doAnswer(
                        invocation -> {
                            HolidayApply request = invocation.getArgument(0);
                            org.junit.jupiter.api.Assertions.assertEquals(
                                    "2026-11-01 00:00:00 - 2026-11-01 00:00:00", request.getTime());
                            request.setId(4);
                            request.setStatus(1);
                            return null;
                        })
                .when(workflow)
                .saveHolidayApply(any());
        String json =
                validLeave
                        .replace("2026-11-01T09:00:00", "2026-11-01T00:00:00")
                        .replace("2026-11-02T09:00:00", "2026-11-01T00:00:00");
        mvc.perform(
                        post("/api/leave-requests")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void reversedLeaveDatesReturn400() throws Exception {
        MockHttpSession s = session();
        String json = validLeave.replace("2026-11-02T09:00:00", "2026-10-31T09:00:00");
        mvc.perform(
                        post("/api/leave-requests")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("End date must be on or after start date"));
    }

    @Test
    void detailsIncludeProgressWithoutEngineIdentifiers() throws Exception {
        HolidayApply request = leave();
        when(workflow.findOwnApplication(4)).thenReturn(request);
        WorkflowProgress progress = new WorkflowProgress();
        progress.setCurrentStage("HR");
        progress.setMessage("Awaiting HR approval");
        progress.setStages(List.of(new WorkflowProgressStage("HR", "HR", "CURRENT")));
        when(context.getBean(WorkflowProgressService.class).describe(request)).thenReturn(progress);
        mvc.perform(get("/api/leave-requests/4").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowProgress.currentStage").value("HR"))
                .andExpect(jsonPath("$.workflowProgress.stages[0].status").value("CURRENT"))
                .andExpect(jsonPath("$.workflowProgress.taskId").doesNotExist())
                .andExpect(jsonPath("$.processInstanceId").doesNotExist());
    }

    @Test
    void foreignLeaveAccessReturns403() throws Exception {
        when(workflow.findOwnApplication(4))
                .thenThrow(
                        new org.springframework.security.access.AccessDeniedException(
                                "internal detail"));
        mvc.perform(get("/api/leave-requests/4").session(session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
        verifyNoInteractions(context.getBean(WorkflowProgressService.class));
    }

    @Test
    void ownedLeaveReturnsResponseDto() throws Exception {
        when(workflow.findOwnApplication(4)).thenReturn(leave());
        mvc.perform(get("/api/leave-requests/4").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.processInstanceId").doesNotExist())
                .andExpect(jsonPath("$.isValid").doesNotExist());
    }

    @Test
    void permittedArchiveReturns204() throws Exception {
        MockHttpSession s = session();
        when(context.getBean(HolidayApplyRepository.class).findLockedById(4)).thenReturn(leave());
        mvc.perform(
                        post("/api/leave-requests/4/archive")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isNoContent());
        verify(context.getBean(HolidayApplyRepository.class)).saveAndFlush(any());
        verify(workflow, never()).deleteHolidayApply(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {3, 4, 5})
    void archivePreservesTerminalStatusValidityAndAllHistory(int status) throws Exception {
        HolidayApply h = leave();
        h.setStatus(status);
        var repo = context.getBean(HolidayApplyRepository.class);
        when(repo.findLockedById(4)).thenReturn(h);
        MockHttpSession owner = session();
        mvc.perform(
                        post("/api/leave-requests/4/archive")
                                .session(owner)
                                .header("X-CSRF-TOKEN", csrf(owner)))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(h.isApplicantArchived());
        org.junit.jupiter.api.Assertions.assertEquals(status, h.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1, h.getIsValid());
        org.junit.jupiter.api.Assertions.assertEquals("process", h.getProcessInstanceId());
        verify(repo).saveAndFlush(h);
        verify(repo, never()).delete(any());
        verify(repo, never()).deleteById(any());
        verifyNoInteractions(
                context.getBean(HolidayApprovalRepository.class),
                context.getBean(org.activiti.engine.TaskService.class),
                workflow);
        mvc.perform(
                        post("/api/leave-requests/4/archive")
                                .session(owner)
                                .header("X-CSRF-TOKEN", csrf(owner)))
                .andExpect(status().isNoContent());
        verify(repo, times(1)).saveAndFlush(h);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2})
    void archiveRejectsNonterminalStates(int state) throws Exception {
        HolidayApply h = leave();
        h.setStatus(state);
        var repo = context.getBean(HolidayApplyRepository.class);
        when(repo.findLockedById(4)).thenReturn(h);
        MockHttpSession owner = session();
        mvc.perform(
                        post("/api/leave-requests/4/archive")
                                .session(owner)
                                .header("X-CSRF-TOKEN", csrf(owner)))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertFalse(h.isApplicantArchived());
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void archiveRequiresOwnershipAndCsrfAndKeepsOldDeleteUnavailable() throws Exception {
        HolidayApply h = leave();
        h.setAccountId(99);
        var repo = context.getBean(HolidayApplyRepository.class);
        when(repo.findLockedById(4)).thenReturn(h);
        MockHttpSession owner = session();
        mvc.perform(post("/api/leave-requests/4/archive").session(owner))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/leave-requests/4/archive")
                                .session(owner)
                                .header("X-CSRF-TOKEN", csrf(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        delete("/api/leave-requests/4")
                                .session(owner)
                                .header("X-CSRF-TOKEN", csrf(owner)))
                .andExpect(status().isMethodNotAllowed());
        verify(repo, never()).saveAndFlush(any());
        verify(workflow, never()).deleteHolidayApply(any());
    }

    @Test
    void applicantListAndItsDashboardTotalUseArchiveFilteredRepositoryPage() throws Exception {
        var repo = context.getBean(HolidayApplyRepository.class);
        when(repo.findByAccountIdAndIsValidAndApplicantArchivedFalse(eq(8), eq(1), any()))
                .thenReturn(
                        new org.springframework.data.domain.PageImpl<>(
                                Collections.singletonList(leave()),
                                org.springframework.data.domain.PageRequest.of(0, 20),
                                1));
        mvc.perform(get("/api/leave-requests").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
        verify(repo).findByAccountIdAndIsValidAndApplicantArchivedFalse(eq(8), eq(1), any());
        verify(repo, never()).findByAccountIdAndIsValid(any(), any(), any());
    }

    @Test
    void activeLeaveCannotBeArchivedOrEdited() throws Exception {
        MockHttpSession s = session();
        HolidayApply h = leave();
        h.setStatus(2);
        when(workflow.findOwnApplication(4)).thenReturn(h);
        when(context.getBean(HolidayApplyRepository.class).findLockedById(4)).thenReturn(h);
        String token = csrf(s);
        mvc.perform(post("/api/leave-requests/4/archive").session(s).header("X-CSRF-TOKEN", token))
                .andExpect(status().isConflict());
        mvc.perform(
                        put("/api/leave-requests/4")
                                .session(s)
                                .header("X-CSRF-TOKEN", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validLeave))
                .andExpect(status().isConflict());
        verify(workflow, never()).updateById(any());
    }

    @Test
    void invalidEmployeeEmailReturns400() throws Exception {
        MockHttpSession s = session("100211");
        mvc.perform(
                        post("/api/employees")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Person\",\"email\":\"invalid\",\"mobile\":\"123\",\"initialPassword\":\"long-safe-passphrase\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }

    @Test
    void validEmployeeCreateReturnsDto201() throws Exception {
        EmployeeCreatedResponse response = new EmployeeCreatedResponse();
        response.setId(7);
        response.setName("Person");
        when(admin.createEmployee(any())).thenReturn(response);
        MockHttpSession s = session("100211");
        mvc.perform(
                        post("/api/employees")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Person\",\"email\":\"person@example.com\",\"mobile\":\"123\",\"initialPassword\":\"long-safe-passphrase\",\"id\":999,\"status\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void resourceNotFoundIs404() throws Exception {
        when(admin.getEmployee(7)).thenThrow(new ApiException(404, "Resource not found"));
        mvc.perform(get("/api/employees/7").session(session("100214")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void internalExceptionDetailsAreNotExposed() throws Exception {
        when(admin.getEmployee(7))
                .thenThrow(new IllegalStateException("SQL password secret stack trace"));
        mvc.perform(get("/api/employees/7").session(session("100214")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"));
    }

    @Test
    void legacyBusinessMessagesAreSanitizedToEnglish409() throws Exception {
        when(admin.getEmployee(7))
                .thenThrow(
                        new com.ada.approval.exception.ParamException("Internal database error"));
        mvc.perform(get("/api/employees/7").session(session("100214")))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value("Operation conflicts with existing data or business rules"));
    }

    @Test
    void approvalRequiresWorkbenchGrant() throws Exception {
        mvc.perform(get("/api/approvals/tasks").session(session()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(workflow);
    }

    @Test
    void taskRequestMismatchRejectsWithoutProcessing() throws Exception {
        MockHttpSession s = session("workbench-code");
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("/workBench/toTaskListPage"));
        doThrow(
                        new com.ada.approval.exception.ParamException(
                                "Task does not belong to this leave workflow"))
                .when(workflow)
                .validateTask(4, "task");
        mvc.perform(
                        post("/api/approvals/tasks/task/approve")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"leaveRequestId\":4}"))
                .andExpect(status().isConflict());
        verify(workflow, never()).processTask(anyInt(), anyString(), anyBoolean(), any());
    }

    @Test
    void approvalDelegatesToExistingSecuredWorkflow() throws Exception {
        MockHttpSession s = session("workbench-code");
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("/workBench/toTaskListPage"));
        mvc.perform(
                        post("/api/approvals/tasks/task/approve")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"leaveRequestId\":4,\"comment\":\"Approved\",\"approverAccountId\":999}"))
                .andExpect(status().isNoContent());
        verify(workflow).validateTask(4, "task");
        verify(workflow).processTask(4, "task", true, "Approved");
    }

    @Test
    void collectionUsesRealJpaPaginationMetadata() throws Exception {
        MockHttpSession s = session();
        HolidayApply h = leave();
        when(context.getBean(HolidayApplyRepository.class)
                        .findByAccountIdAndIsValidAndApplicantArchivedFalse(eq(8), eq(1), any()))
                .thenReturn(
                        new org.springframework.data.domain.PageImpl<>(
                                Collections.singletonList(h),
                                org.springframework.data.domain.PageRequest.of(1, 20),
                                45));
        mvc.perform(get("/api/leave-requests?page=1&size=20").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(45))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void invalidPageSizeReturns400() throws Exception {
        mvc.perform(get("/api/leave-requests?size=1000").session(session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400WithoutParserInternals() throws Exception {
        MockHttpSession s = session();
        mvc.perform(
                        post("/api/leave-requests")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request values or JSON"));
    }

    @Test
    void taskSpecificDenialRemains403WithWorkbenchGrant() throws Exception {
        MockHttpSession s = session("workbench-code");
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("/workBench/toTaskListPage"));
        doThrow(
                        new org.springframework.security.access.AccessDeniedException(
                                "Not this task assignee"))
                .when(workflow)
                .validateTask(4, "task");
        mvc.perform(
                        post("/api/approvals/tasks/task/approve")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"leaveRequestId\":4}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
        verify(workflow, never()).processTask(anyInt(), anyString(), anyBoolean(), any());
    }

    @Test
    void rejectRouteDelegatesRejectionAndReturns204() throws Exception {
        MockHttpSession s = session("workbench-code");
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("/workBench/toTaskListPage"));
        mvc.perform(
                        post("/api/approvals/tasks/task/reject")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"leaveRequestId\":4,\"comment\":\"Rejected\"}"))
                .andExpect(status().isNoContent());
        verify(workflow).processTask(4, "task", false, "Rejected");
    }

    @Test
    void negativeResourceIdReturns400BeforeServiceCall() throws Exception {
        mvc.perform(get("/api/employees/-1").session(session("100214")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }

    @Test
    void apiLogoutInvalidatesSessionAndReturns204() throws Exception {
        MockHttpSession s = session();
        mvc.perform(post("/api/auth/logout").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isNoContent());
        assertTrue(s.isInvalid());
    }

    @Test
    void approvalHistoryReturnsAuditDtosForOwner() throws Exception {
        when(workflow.findOwnApplication(4)).thenReturn(leave());
        HolidayApproval a = new HolidayApproval();
        a.setId(2);
        a.setTaskId("old-task");
        a.setTaskDefKey("manager_check");
        a.setResult(1);
        a.setUserName("manager");
        when(context.getBean(HolidayApprovalRepository.class)
                        .findByProcessInstanceIdOrderByCreateTimeAscIdAsc("process"))
                .thenReturn(Collections.singletonList(a));
        mvc.perform(get("/api/leave-requests/4/approval-history").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stage").value("DEPARTMENT_MANAGER"))
                .andExpect(jsonPath("$[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$[0].taskId").value("old-task"))
                .andExpect(jsonPath("$[0].processInstanceId").doesNotExist());
    }

    @Test
    void unsupportedApiMethodDoesNotFallBackToLegacy200Error() throws Exception {
        MockHttpSession s = session();
        mvc.perform(
                        patch("/api/leave-requests/4")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validLeave))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("HTTP method not supported"));
    }

    @Test
    void leaveDetailIncludesApplicantNameAndSubmissionTimeFromExistingData() throws Exception {
        HolidayApply h = leave();
        h.setUserName("unreliable legacy value");
        h.setSubmitTime(
                Date.from(
                        java.time.LocalDateTime.of(2026, 11, 1, 9, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()));
        when(workflow.findOwnApplication(4)).thenReturn(h);
        when(context.getBean(AccountRepository.class).findApplicantNames(Collections.singleton(8)))
                .thenReturn(Collections.singletonList(new Object[] {8, "Alice Employee"}));
        mvc.perform(get("/api/leave-requests/4").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantName").value("Alice Employee"))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-01T09:00:00"));
    }

    @Test
    void leavePageUsesOneBatchedApplicantProjection() throws Exception {
        HolidayApply first = leave(), second = leave();
        second.setId(5);
        when(context.getBean(HolidayApplyRepository.class)
                        .findByAccountIdAndIsValidAndApplicantArchivedFalse(eq(8), eq(1), any()))
                .thenReturn(
                        new org.springframework.data.domain.PageImpl<>(
                                Arrays.asList(first, second)));
        AccountRepository repo = context.getBean(AccountRepository.class);
        when(repo.findApplicantNames(Collections.singleton(8)))
                .thenReturn(Collections.singletonList(new Object[] {8, "Alice"}));
        mvc.perform(get("/api/leave-requests").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicantName").value("Alice"))
                .andExpect(jsonPath("$.content[1].applicantName").value("Alice"));
        verify(repo, times(1)).findApplicantNames(Collections.singleton(8));
        verify(repo, never()).findById(any());
    }

    @Test
    void approvalPageBatchesDifferentApplicants() throws Exception {
        com.ada.approval.service.projection.LeaveTaskRow
                first = new com.ada.approval.service.projection.LeaveTaskRow(),
                second = new com.ada.approval.service.projection.LeaveTaskRow();
        first.setAccountId(8);
        first.setTaskId("task-a");
        second.setAccountId(9);
        second.setTaskId("task-b");
        Map<String, Object> result = new HashMap<>();
        result.put("data", Arrays.asList(first, second));
        result.put("count", 2);
        when(workflow.queryMyTaskList(any())).thenReturn(result);
        AccountRepository repo = context.getBean(AccountRepository.class);
        when(repo.findApplicantNames(any()))
                .thenReturn(Arrays.asList(new Object[] {8, "Alice"}, new Object[] {9, "Bob"}));
        MockHttpSession session = session("workbench-code");
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("/workBench/toTaskListPage"));
        mvc.perform(get("/api/approvals/tasks").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].leaveRequest.applicantName").value("Alice"))
                .andExpect(jsonPath("$.content[1].leaveRequest.applicantName").value("Bob"));
        verify(repo, times(1)).findApplicantNames(new HashSet<>(Arrays.asList(8, 9)));
    }

    @Test
    void incompleteApplicantAndSubmissionDataRemainNullable() throws Exception {
        HolidayApply h = leave();
        h.setAccountId(null);
        when(workflow.findOwnApplication(4)).thenReturn(h);
        mvc.perform(get("/api/leave-requests/4").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantName").isEmpty())
                .andExpect(jsonPath("$.submittedAt").isEmpty());
        verifyNoInteractions(context.getBean(AccountRepository.class));
    }

    @Test
    void employeeFormLookupUsesCreateOrUpdatePermissionOnly() throws Exception {
        EmployeeFormOptions options = new EmployeeFormOptions();
        options.setDepartments(Collections.emptyList());
        when(admin.employeeFormOptions()).thenReturn(options);
        mvc.perform(get("/api/employees/form-options").session(session("100211")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/employees/form-options").session(session("100212")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/employees/form-options").session(session("100214")))
                .andExpect(status().isForbidden());
        verify(admin, times(2)).employeeFormOptions();
    }

    @Test
    void managerLookupUsesDepartmentPermissionAndMinimalDto() throws Exception {
        ReferenceResponse manager = new ReferenceResponse();
        manager.setId(3);
        manager.setName("Manager");
        when(admin.managerOptions(0, 100, null))
                .thenReturn(new PageResponse<>(Collections.singletonList(manager), 0, 100, 1));
        mvc.perform(get("/api/departments/manager-options").session(session("10011")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Manager"))
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
        mvc.perform(get("/api/departments/manager-options").session(session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemOnlyAdminCannotCreateLeaveEvenWithNumericAdminPermissions() throws Exception {
        MockHttpSession s = session("100211");
        when(context.getBean(com.ada.approval.workflow.ApplicantWorkflow.class).canApply(any()))
                .thenReturn(false);
        mvc.perform(
                        post("/api/leave-requests")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validLeave))
                .andExpect(status().isForbidden());
        verify(workflow, never()).saveHolidayApply(any());
    }

    @Test
    void withdrawalEndpointRequiresSession() throws Exception {
        var anonymous = new MockHttpSession();
        mvc.perform(
                        post("/api/leave/4/withdraw")
                                .session(anonymous)
                                .header("X-CSRF-TOKEN", csrf(anonymous)))
                .andExpect(status().isUnauthorized());
        verify(context.getBean(LeaveWithdrawalService.class), never()).withdraw(any());
    }

    @Test
    void withdrawalRequiresCsrf() throws Exception {
        mvc.perform(post("/api/leave/4/withdraw").session(session()))
                .andExpect(status().isForbidden());
        verify(context.getBean(LeaveWithdrawalService.class), never()).withdraw(any());
    }

    @Test
    void withdrawalUsesPostAndReturns204() throws Exception {
        var s = session();
        mvc.perform(post("/api/leave/4/withdraw").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isNoContent());
        verify(context.getBean(LeaveWithdrawalService.class)).withdraw(4);
    }

    @Test
    void withdrawalReturnsOwnership403() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("Not applicant"))
                .when(context.getBean(LeaveWithdrawalService.class))
                .withdraw(4);
        var s = session("100211");
        mvc.perform(post("/api/leave/4/withdraw").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawalReturnsConflict409AndNotFound404() throws Exception {
        var bean = context.getBean(LeaveWithdrawalService.class);
        var s = session();
        doThrow(new ApiException(409, "Ended")).when(bean).withdraw(4);
        mvc.perform(post("/api/leave/4/withdraw").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isConflict());
        doThrow(new ApiException(404, "Not found")).when(bean).withdraw(4);
        mvc.perform(post("/api/leave/4/withdraw").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerDetailsExposeBackendWithdrawalCapability() throws Exception {
        HolidayApply h = leave();
        h.setStatus(2);
        when(workflow.findOwnApplication(4)).thenReturn(h);
        when(context.getBean(LeaveWithdrawalService.class).canWithdraw(eq(h), any()))
                .thenReturn(true);
        mvc.perform(get("/api/leave-requests/4").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canWithdraw").value(true))
                .andExpect(jsonPath("$.applicantAccountId").value(8));
    }

    @Test
    void enrichedEmployeeResponseRetainsReadAuthorization() throws Exception {
        EmployeeResponse dto = new EmployeeResponse();
        dto.setId(9);
        dto.setDepartmentName("Operations");
        dto.setJobTitleName("HR");
        dto.setEmployeeStatusName("Active");
        when(admin.getEmployee(9)).thenReturn(dto);
        mvc.perform(get("/api/employees/9").session(session())).andExpect(status().isForbidden());
        verify(admin, never()).getEmployee(9);
        mvc.perform(get("/api/employees/9").session(session("100214")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.departmentName").value("Operations"))
                .andExpect(jsonPath("$.jobTitleName").value("HR"))
                .andExpect(jsonPath("$.employeeStatusName").value("Active"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void leaveTypeCreateRequiresAuthenticationPermissionAndCsrf() throws Exception {
        mvc.perform(
                        post("/api/admin/leave-types")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Annual Leave\"}"))
                .andExpect(status().isForbidden());
        MockHttpSession employee = session();
        mvc.perform(
                        post("/api/admin/leave-types")
                                .session(employee)
                                .header("X-CSRF-TOKEN", csrf(employee))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Annual Leave\"}"))
                .andExpect(status().isForbidden());
        MockHttpSession adminSession = session("10023");
        mvc.perform(
                        post("/api/admin/leave-types")
                                .session(adminSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Annual Leave\"}"))
                .andExpect(status().isForbidden());
        ReferenceResponse dto = new ReferenceResponse();
        dto.setId(3);
        dto.setName("Annual Leave");
        when(admin.createLeaveType(any())).thenReturn(dto);
        mvc.perform(
                        post("/api/admin/leave-types")
                                .session(adminSession)
                                .header("X-CSRF-TOKEN", csrf(adminSession))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"  Annual Leave  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/admin/leave-types/3"))
                .andExpect(jsonPath("$.name").value("Annual Leave"));
        verify(admin).createLeaveType(argThat(body -> "Annual Leave".equals(body.getName())));
    }

    @Test
    void leaveTypeUpdateAndDeleteEnforcePermissionsAndReturnNormalErrors() throws Exception {
        MockHttpSession employee = session();
        mvc.perform(
                        put("/api/admin/leave-types/3")
                                .session(employee)
                                .header("X-CSRF-TOKEN", csrf(employee))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Sick Leave\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        delete("/api/admin/leave-types/3")
                                .session(employee)
                                .header("X-CSRF-TOKEN", csrf(employee)))
                .andExpect(status().isForbidden());
        MockHttpSession authorized = session("10023");
        mvc.perform(
                        put("/api/admin/leave-types/3")
                                .session(authorized)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Sick Leave\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/leave-types/3").session(authorized))
                .andExpect(status().isForbidden());
        ReferenceResponse dto = new ReferenceResponse();
        dto.setId(3);
        dto.setName("Sick Leave");
        when(admin.updateLeaveType(eq(3), any())).thenReturn(dto);
        mvc.perform(
                        put("/api/admin/leave-types/3")
                                .session(authorized)
                                .header("X-CSRF-TOKEN", csrf(authorized))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Sick Leave\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sick Leave"));
        doThrow(
                        new ApiException(
                                409,
                                "This leave type is already used by existing requests and cannot be deleted."))
                .when(admin)
                .deleteLeaveType(3);
        mvc.perform(
                        delete("/api/admin/leave-types/3")
                                .session(authorized)
                                .header("X-CSRF-TOKEN", csrf(authorized)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "This leave type is already used by existing requests and cannot be deleted."));
        mvc.perform(
                        delete("/api/admin/leave-types/4")
                                .session(authorized)
                                .header("X-CSRF-TOKEN", csrf(authorized)))
                .andExpect(status().isNoContent());
        when(admin.getLeaveType(99)).thenThrow(new ApiException(404, "Resource not found"));
        mvc.perform(get("/api/admin/leave-types/99").session(authorized))
                .andExpect(status().isNotFound());
    }

    @Test
    void leaveTypeValidationAndReferenceReadsRemainProtected() throws Exception {
        MockHttpSession s = session("10023");
        for (String body :
                Arrays.asList(
                        "{}",
                        "{\"name\":\"   \"}",
                        "{\"name\":\"" + String.join("", Collections.nCopies(256, "x")) + "\"}")) {
            mvc.perform(
                            post("/api/admin/leave-types")
                                    .session(s)
                                    .header("X-CSRF-TOKEN", csrf(s))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(admin, never()).createLeaveType(any());
        mvc.perform(get("/api/admin/leave-types")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/leave-types").session(session()))
                .andExpect(status().isForbidden());
        when(admin.leaveTypes(0, 20))
                .thenReturn(new PageResponse<>(Collections.emptyList(), 0, 20, 0));
        mvc.perform(get("/api/leave-types").session(session())).andExpect(status().isOk());
        mvc.perform(get("/api/leave-types")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeProfilePayloadCannotControlStatusAndMissingActiveIsSanitized() throws Exception {
        MockHttpSession s = session("100211");
        EmployeeCreatedResponse response = new EmployeeCreatedResponse();
        response.setId(7);
        response.setEmployeeStatusName("Active");
        when(admin.createEmployee(any())).thenReturn(response);
        mvc.perform(
                        post("/api/employees")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Person\",\"email\":\"person@example.test\",\"mobile\":\"555\",\"initialPassword\":\"long-safe-passphrase\",\"employeeStatusId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeStatusName").value("Active"));
        verify(admin)
                .createEmployee(
                        argThat(
                                dto ->
                                        com.ada.approval.api.dto.ApiMapper.employee(dto)
                                                        .getEmployStatusId()
                                                == null));
        when(admin.createEmployee(any()))
                .thenThrow(
                        new ApiException(
                                500,
                                "Active employee status is unavailable. Please contact an administrator."));
        mvc.perform(
                        post("/api/employees")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Person\",\"email\":\"person@example.test\",\"mobile\":\"555\",\"initialPassword\":\"long-safe-passphrase\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Active employee status is unavailable. Please contact an administrator."));
    }

    @Test
    void employeePasswordIsReturnedOnlyByCreateAndIsNotCacheable() throws Exception {
        EmployeeCreatedResponse created = new EmployeeCreatedResponse();
        created.setId(7);
        created.setTemporaryPassword("test-only-response-value");
        when(admin.createEmployee(any())).thenReturn(created);
        MockHttpSession s = session("100211");
        mvc.perform(
                        post("/api/employees")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Person\",\"email\":\"person@example.test\",\"mobile\":\"555\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.temporaryPassword").value("test-only-response-value"));
        EmployeeResponse ordinary = new EmployeeResponse();
        ordinary.setId(7);
        when(admin.getEmployee(7)).thenReturn(ordinary);
        mvc.perform(get("/api/employees/7").session(session("100214")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }

    @Test
    void selfServicePasswordChangeRequiresCsrfUsesNoClientAccountIdAndEndsSession()
            throws Exception {
        MockHttpSession s = session();
        mvc.perform(
                        put("/api/auth/password")
                                .session(s)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"old-safe-passphrase\",\"newPassword\":\"new-safe-passphrase\",\"confirmation\":\"new-safe-passphrase\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/auth/password")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"accountId\":999,\"currentPassword\":\"old-safe-passphrase\",\"newPassword\":\"new-safe-passphrase\",\"confirmation\":\"new-safe-passphrase\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(accounts)
                .updatePassword(
                        "old-safe-passphrase", "new-safe-passphrase", "new-safe-passphrase");
        assertTrue(s.isInvalid());
    }

    @Test
    void selfServicePasswordValidationFailsBeforeServiceCall() throws Exception {
        MockHttpSession s = session();
        for (String body :
                Arrays.asList(
                        "{\"currentPassword\":\"old-safe-passphrase\",\"newPassword\":\"short\",\"confirmation\":\"short\"}",
                        "{\"currentPassword\":\"old-safe-passphrase\",\"newPassword\":\"new-safe-passphrase\",\"confirmation\":\"mismatch\"}")) {
            mvc.perform(
                            put("/api/auth/password")
                                    .session(s)
                                    .header("X-CSRF-TOKEN", csrf(s))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(accounts, never()).updatePassword(any(), any(), any());
        mvc.perform(
                        put("/api/auth/password")
                                .header("X-CSRF-TOKEN", csrf(new MockHttpSession()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession historySession() {
        var s = session();
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(List.of("/workBench/toTaskListPage"));
        return s;
    }

    @Test
    void approvalHistoryRequiresWorkbenchAndAuthentication() throws Exception {
        mvc.perform(get("/api/approvals/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/approvals/history").session(session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/approvals/history/4").session(session()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(HolidayApprovalRepository.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void personalHistoryIncludesArchivedRequestsAndBothDecisionTypes(int result) throws Exception {
        var s = historySession();
        var audits = context.getBean(HolidayApprovalRepository.class);
        var requests = context.getBean(HolidayApplyRepository.class);
        var a = new HolidayApproval();
        a.setId(9);
        a.setUserId(8);
        a.setResult(result);
        a.setProcessInstanceId("process");
        a.setTaskDefKey("manager_check");
        a.setCreateTime(java.time.LocalDateTime.of(2026, 10, 7, 22, 15));
        var row = leave();
        row.setApplicantArchived(true);
        row.setAccountId(90);
        row.setTitle("Family leave");
        row.setHolidayType(1);
        row.setStartTime(java.sql.Timestamp.valueOf("2026-12-15 00:00:00"));
        row.setEndTime(java.sql.Timestamp.valueOf("2026-12-20 00:00:00"));
        when(context.getBean(AccountRepository.class).findApplicantNames(Set.of(90)))
                .thenReturn(Collections.singletonList(new Object[] {90, "Employee A"}));
        HolidayType type = new HolidayType();
        type.setId(1);
        type.setHolidayType("Annual Leave");
        when(context.getBean(HolidayTypeRepository.class).findAllById(Set.of(1)))
                .thenReturn(List.of(type));
        when(audits.findDecisionHistory(eq(8), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(a)));
        when(requests.findByProcessInstanceIdIn(Set.of("process"))).thenReturn(List.of(row));
        mvc.perform(get("/api/approvals/history").param("accountId", "999").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId").value(4))
                .andExpect(jsonPath("$.content[0].applicantName").value("Employee A"))
                .andExpect(jsonPath("$.content[0].leaveType").value("Annual Leave"))
                .andExpect(jsonPath("$.content[0].stage").value("DEPARTMENT_MANAGER"))
                // This explicit MVC fixture uses Jackson's default timestamp array format.
                .andExpect(jsonPath("$.content[0].decidedAt[0]").value(2026))
                .andExpect(jsonPath("$.content[0].decidedAt[3]").value(22))
                .andExpect(jsonPath("$.content[0].decidedAt[4]").value(15))
                .andExpect(
                        jsonPath("$.content[0].decision")
                                .value(result % 2 == 1 ? "APPROVED" : "REJECTED"));
        verify(audits).findDecisionHistory(eq(8), any());
        verify(requests, never())
                .findByAccountIdAndIsValidAndApplicantArchivedFalse(any(), any(), any());
        verifyNoInteractions(workflow, context.getBean(org.activiti.engine.TaskService.class));
        assertTrue(row.isApplicantArchived());
    }

    @Test
    void historyQueryExcludesWithdrawalsAndOtherAccountsWithoutArchiveFiltering() throws Exception {
        var method =
                HolidayApprovalRepository.class.getMethod(
                        "findDecisionHistory",
                        Integer.class,
                        org.springframework.data.domain.Pageable.class);
        String query =
                method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertTrue(query.contains("a.userId = :accountId"));
        assertTrue(query.contains("a.result in (1, 2, 3, 4, 5, 6)"));
        assertTrue(query.contains("h.processInstanceId = a.processInstanceId"));
        assertFalse(query.contains("applicantArchived"));
        assertFalse(query.contains("isValid"));
    }

    @Test
    void historicalDetailsRequireActualDecisionAndIgnoreArchiveAndActiveTasks() throws Exception {
        var s = historySession();
        var requests = context.getBean(HolidayApplyRepository.class);
        var audits = context.getBean(HolidayApprovalRepository.class);
        var row = leave();
        row.setAccountId(90);
        row.setApplicantArchived(true);
        when(requests.findById(4)).thenReturn(Optional.of(row));
        when(requests.findByProcessInstanceIdIn(Set.of("process"))).thenReturn(List.of(row));
        when(audits.existsByProcessInstanceIdAndUserIdAndResultIn(
                        "process", 8, List.of(1, 2, 3, 4, 5, 6)))
                .thenReturn(true);
        when(audits.findByProcessInstanceIdOrderByCreateTimeAscIdAsc("process"))
                .thenReturn(List.of());
        mvc.perform(get("/api/approvals/history/4").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveRequest.id").value(4))
                .andExpect(jsonPath("$.leaveRequest.canWithdraw").value(false));
        verifyNoInteractions(
                workflow,
                context.getBean(org.activiti.engine.TaskService.class),
                context.getBean(LeaveWithdrawalService.class));
        assertTrue(row.isApplicantArchived());
    }

    @Test
    void historicalDetailsRejectNeverActedWithdrawalOnlyOrDifferentProcess() throws Exception {
        var s = historySession();
        var requests = context.getBean(HolidayApplyRepository.class);
        var row = leave();
        row.setProcessInstanceId("different-process");
        when(requests.findById(4)).thenReturn(Optional.of(row));
        mvc.perform(get("/api/approvals/history/4").session(s)).andExpect(status().isForbidden());
        verify(context.getBean(HolidayApprovalRepository.class))
                .existsByProcessInstanceIdAndUserIdAndResultIn(
                        "different-process", 8, List.of(1, 2, 3, 4, 5, 6));
    }

    @Test
    void historicalDetailsRejectAmbiguousRequestProcessCorrelation() throws Exception {
        var s = historySession();
        var requests = context.getBean(HolidayApplyRepository.class);
        var audits = context.getBean(HolidayApprovalRepository.class);
        when(requests.findById(4)).thenReturn(Optional.of(leave()));
        when(audits.existsByProcessInstanceIdAndUserIdAndResultIn(
                        "process", 8, List.of(1, 2, 3, 4, 5, 6)))
                .thenReturn(true);
        var other = leave();
        other.setId(99);
        when(requests.findByProcessInstanceIdIn(Set.of("process"))).thenReturn(List.of(other));
        mvc.perform(get("/api/approvals/history/4").session(s)).andExpect(status().isForbidden());
    }

    @Test
    void historyUsesBoundedPaginationAndHandlesEmptyOrMissingResources() throws Exception {
        var s = historySession();
        var audits = context.getBean(HolidayApprovalRepository.class);
        var page = org.springframework.data.domain.PageRequest.of(1, 10);
        when(audits.findDecisionHistory(8, page))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), page, 10));
        mvc.perform(get("/api/approvals/history?page=1&size=10").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(10));
        verify(audits).findDecisionHistory(8, page);
        verifyNoInteractions(context.getBean(HolidayApplyRepository.class));
        mvc.perform(get("/api/approvals/history?size=101").session(s))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/approvals/history/999").session(s)).andExpect(status().isNotFound());
    }
}
