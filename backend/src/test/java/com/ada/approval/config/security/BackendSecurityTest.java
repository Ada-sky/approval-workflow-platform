package com.ada.approval.config.security;

import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.*;
import com.ada.approval.service.impl.HolidayApplyServiceImpl;
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real security filters in an explicit MVC fixture; no Boot auto-configuration, database or engine.
 */
class BackendSecurityTest {
    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Fixture {
        @Bean
        com.ada.approval.workflow.ApplicantWorkflow applicantWorkflow() {
            return mock(com.ada.approval.workflow.ApplicantWorkflow.class);
        }

        @Bean
        org.activiti.engine.RepositoryService processDefinitions() {
            return mock(org.activiti.engine.RepositoryService.class);
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
        HolidayApplyRepository applications() {
            return mock(HolidayApplyRepository.class);
        }

        @Bean
        org.activiti.engine.TaskService tasks() {
            return mock(org.activiti.engine.TaskService.class);
        }

        @Bean
        org.activiti.engine.RuntimeService runtime() {
            return mock(org.activiti.engine.RuntimeService.class);
        }

        @Bean
        IEmployeeService employees() {
            return mock(IEmployeeService.class);
        }

        @Bean
        IHolidayApprovalService approvals() {
            return mock(IHolidayApprovalService.class);
        }

        @Bean
        IHolidayApplyService applicationService(HolidayApplyRepository applications) {
            return new HolidayApplyServiceImpl(applications);
        }

        @Bean
        Probe endpoints() {
            return new Probe();
        }
    }

    @RestController
    static class Probe {
        @RequestMapping({
            "/index",
            "/main",
            "/api/departments",
            "/api/departments",
            "/api/roles",
            "/api/menus",
            "/api/employees",
            "/api/approvals",
            "/api/auth/password"
        })
        String ok() {
            return "ok";
        }

        @PostMapping("/api/auth/logout")
        @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
        void logout(jakarta.servlet.http.HttpServletRequest request) {
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private IAccountService accounts;
    private IPermissionService permissions;
    private PermissionRepository grants;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Fixture.class);
        context.refresh();
        mvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .addFilters(context.getBean(FilterChainProxy.class))
                        .build();
        accounts = context.getBean(IAccountService.class);
        permissions = context.getBean(IPermissionService.class);
        grants = context.getBean(PermissionRepository.class);
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private Account account() {
        Account a = new Account();
        a.setId(8);
        a.setStatus(1);
        a.setUserName("employee");
        a.setPassword("encoded-password");
        return a;
    }

    private MockHttpSession session(String... authorities) {
        Account a = account();
        a.setGrantedAuthority(AuthorityUtils.createAuthorityList(authorities));
        when(accounts.findAccountByUserName("employee")).thenReturn(a);
        when(permissions.findAuthorityByUserName("employee"))
                .thenReturn(Arrays.asList(authorities));
        SecurityContext security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(
                new UsernamePasswordAuthenticationToken(a, null, a.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return session;
    }

    private String csrf(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, new MockHttpServletResponse());
        new XorCsrfTokenRequestAttributeHandler()
                .handle(request, new MockHttpServletResponse(), () -> token);
        return ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
    }

    @Test
    void legacyLoginPageIsRetiredAndFramesAreDenied() throws Exception {
        mvc.perform(get("/index"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void retiredRoutesAreDeniedEvenWithAdministratorAndWorkbenchGrants() throws Exception {
        MockHttpSession session = session("10011", "10022", "10023", "workbench-code");
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("/workBench/toTaskListPage"));
        for (String path :
                new String[] {
                    "/index",
                    "/main",
                    "/dept/list",
                    "/workBench/toTaskListPage",
                    "/bpmnjs/index.html",
                    "/css/main.css",
                    "/signout",
                    "/login"
                }) mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAdminAccessRequiresLogin() throws Exception {
        mvc.perform(get("/api/departments")).andExpect(status().isUnauthorized());
    }

    @Test
    void normalEmployeeCannotAccessAdministration() throws Exception {
        MockHttpSession s = session();
        for (String path : new String[] {"/api/departments", "/api/roles", "/api/menus"})
            mvc.perform(get(path).session(s)).andExpect(status().isForbidden());
    }

    @Test
    void existingAdminPermissionAllowsDepartmentAccess() throws Exception {
        mvc.perform(get("/api/departments").session(session("10011"))).andExpect(status().isOk());
    }

    @Test
    void employeeCreateRequiresSpecificExistingAuthority() throws Exception {
        MockHttpSession s = session("100211");
        mvc.perform(post("/api/employees").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isOk());
    }

    @Test
    void csrfCannotBeBypassedByAdministrator() throws Exception {
        mvc.perform(post("/api/departments").session(session("10011")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validCsrfAllowsAuthorizedWrite() throws Exception {
        MockHttpSession s = session("10011");
        mvc.perform(post("/api/departments").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isOk());
    }

    @Test
    void loginPostAlsoRequiresCsrf() throws Exception {
        mvc.perform(post("/login").param("userName", "employee").param("password", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginFailureUsesGenericUnauthorizedResponse() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(
                        post("/login")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .param("userName", "unknown")
                                .param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approvalMenuGrantIsRequiredEvenForAssignedUsers() throws Exception {
        mvc.perform(get("/api/approvals").session(session())).andExpect(status().isForbidden());
    }

    @Test
    void existingWorkbenchGrantAllowsApprovalFunctionality() throws Exception {
        MockHttpSession s = session("existing-workbench-code");
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("workBench/toTaskListPage"));
        mvc.perform(get("/api/approvals").session(s)).andExpect(status().isOk());
    }

    @Test
    void disabledAccountLosesExistingSession() throws Exception {
        MockHttpSession s = session("10011");
        Account disabled = account();
        disabled.setStatus(0);
        when(accounts.findAccountByUserName("employee")).thenReturn(disabled);
        mvc.perform(get("/api/departments").session(s)).andExpect(status().isUnauthorized());
        assertTrue(s.isInvalid());
    }

    @Test
    void deletedAccountLosesExistingSession() throws Exception {
        MockHttpSession s = session("10011");
        when(accounts.findAccountByUserName("employee")).thenReturn(null);
        mvc.perform(get("/api/departments").session(s)).andExpect(status().isUnauthorized());
    }

    @Test
    void revokedPermissionDoesNotRemainInSession() throws Exception {
        MockHttpSession s = session("10011");
        when(permissions.findAuthorityByUserName("employee")).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/departments").session(s)).andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeRevokesOldSession() throws Exception {
        MockHttpSession s = session("10011");
        Account changed = account();
        changed.setPassword("new-hash");
        when(accounts.findAccountByUserName("employee")).thenReturn(changed);
        mvc.perform(get("/api/departments").session(s)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownBackendRouteIsDenied() throws Exception {
        mvc.perform(get("/unexpected-admin-route").session(session("10011", "10022", "10023")))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiLogoutRequiresPostWithCsrf() throws Exception {
        MockHttpSession s = session();
        mvc.perform(get("/api/auth/logout").session(s)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/auth/logout").session(s).header("X-CSRF-TOKEN", csrf(s)))
                .andExpect(status().isNoContent());
        assertTrue(s.isInvalid());
    }

    @Test
    void nullAccountStateCannotEnableAccount() {
        Account a = new Account();
        assertFalse(a.isEnabled());
    }

    @Test
    void passwordPolicyRejectsWeakAndTruncatedPasswords() {
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () -> PasswordPolicy.validate("123"));
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () -> PasswordPolicy.validate(String.join("", Collections.nCopies(73, "a"))));
        assertDoesNotThrow(() -> PasswordPolicy.validate("long-safe-passphrase"));
    }

    @Test
    void passwordIsNotSerialized() throws Exception {
        assertFalse(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(account())
                        .contains("encoded-password"));
    }

    @Test
    void serviceOwnershipRejectsAnotherEmployeeAndAcceptsOwner() {
        HolidayApplyRepository r = mock(HolidayApplyRepository.class);
        HolidayApplyServiceImpl service = new HolidayApplyServiceImpl(r);
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "accountService", accounts);
        MockHttpSession s = session();
        SecurityContextHolder.setContext(
                (SecurityContext)
                        s.getAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        HolidayApply h = new HolidayApply();
        h.setId(4);
        h.setAccountId(9);
        h.setStatus(4);
        h.setIsValid(1);
        when(r.findById(4)).thenReturn(Optional.of(h));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.findOwnApplication(4));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.deleteHolidayApply(4));
        verify(r, never()).saveAndFlush(any());
        h.setAccountId(8);
        assertSame(h, service.findOwnApplication(4));
        service.deleteHolidayApply(4);
        assertEquals(Integer.valueOf(0), h.getIsValid());
    }

    @Test
    void methodAuthorizationRequiresWorkbenchGrantBeforeTaskLookup() {
        MockHttpSession s = session();
        SecurityContextHolder.setContext(
                (SecurityContext)
                        s.getAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> context.getBean(IHolidayApplyService.class).validateTask(4, "task"));
        verifyNoInteractions(context.getBean(org.activiti.engine.TaskService.class));
    }

    @Test
    void workbenchGrantDoesNotBypassSpecificTaskAuthorization() {
        MockHttpSession s = session("existing-workbench-code");
        SecurityContextHolder.setContext(
                (SecurityContext)
                        s.getAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        when(grants.findGrantedMenuUrls("employee"))
                .thenReturn(Collections.singletonList("/workBench/toTaskListPage"));
        HolidayApply application = new HolidayApply();
        application.setId(4);
        application.setStatus(2);
        application.setIsValid(1);
        application.setProcessInstanceId("process");
        when(context.getBean(HolidayApplyRepository.class).findById(4))
                .thenReturn(Optional.of(application));
        org.activiti.engine.TaskService tasks =
                context.getBean(org.activiti.engine.TaskService.class);
        org.activiti.engine.task.TaskQuery query =
                mock(org.activiti.engine.task.TaskQuery.class, RETURNS_SELF);
        org.activiti.engine.task.Task task = mock(org.activiti.engine.task.Task.class);
        when(tasks.createTaskQuery()).thenReturn(query);
        doReturn(task).when(query).singleResult();
        when(task.getProcessInstanceId()).thenReturn("process");
        when(task.getTaskDefinitionKey()).thenReturn("hr_check");
        when(task.getAssignee()).thenReturn("other-approver");
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> context.getBean(IHolidayApplyService.class).validateTask(4, "task"));
        when(task.getAssignee()).thenReturn("employee");
        assertDoesNotThrow(
                () -> context.getBean(IHolidayApplyService.class).validateTask(4, "task"));
    }

    @Test
    void successfulFormLoginCreatesUsableSessionWithFreshAuthorities() throws Exception {
        Account a = account();
        a.setPassword(
                context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
                        .encode("long-safe-passphrase"));
        when(accounts.findAccountByUserName("employee")).thenReturn(a);
        when(permissions.findAuthorityByUserName("employee"))
                .thenReturn(Collections.singletonList("10011"));
        MockHttpSession s = new MockHttpSession();
        mvc.perform(
                        post("/login")
                                .session(s)
                                .header("X-CSRF-TOKEN", csrf(s))
                                .param("userName", "employee")
                                .param("password", "long-safe-passphrase"))
                .andExpect(status().isOk());
        assertNotNull(
                s.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        mvc.perform(get("/api/departments").session(s)).andExpect(status().isOk());
    }

    @Test
    void authorityStringsCannotInjectCommaSeparatedPermissions() throws Exception {
        mvc.perform(get("/api/departments").session(session("10011,10022")))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeProvisioningRequiresExplicitPasswordAndStoresOnlyHash() {
        EmployeeRepository r = mock(EmployeeRepository.class);
        AccountRepository ar = mock(AccountRepository.class);
        com.ada.approval.service.impl.EmployeeServiceImpl employees =
                new com.ada.approval.service.impl.EmployeeServiceImpl(r, ar);
        org.springframework.test.util.ReflectionTestUtils.setField(
                employees, "accountService", accounts);
        org.springframework.security.crypto.password.PasswordEncoder encoder =
                mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        org.springframework.test.util.ReflectionTestUtils.setField(
                employees, "passwordEncoder", encoder);
        Employee employee = new Employee();
        employee.setEmpName("Person");
        employee.setMobile("123");
        employee.setEmail("new@example.com");
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () -> employees.saveEmployee(employee));
        verifyNoInteractions(r);
        when(r.findMaxEmpNum()).thenReturn("000001");
        when(r.saveAndFlush(any()))
                .thenAnswer(
                        invocation -> {
                            Employee e = invocation.getArgument(0);
                            e.setId(7);
                            return e;
                        });
        when(encoder.encode("long-safe-passphrase")).thenReturn("bcrypt-hash");
        when(accounts.save(any())).thenReturn(true);
        employees.saveEmployee(employee, "long-safe-passphrase");
        verify(accounts)
                .save(
                        argThat(
                                a ->
                                        "bcrypt-hash".equals(a.getPassword())
                                                && Integer.valueOf(1).equals(a.getStatus())
                                                && Integer.valueOf(7).equals(a.getEmpId())));
        verify(encoder, never()).encode("123");
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () -> employees.saveEmployee(employee, "long-safe-passphrase"));
        verify(r, times(1)).saveAndFlush(any());
    }
}
