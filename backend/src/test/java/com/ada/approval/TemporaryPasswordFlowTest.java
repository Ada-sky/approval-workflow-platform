package com.ada.approval;

import com.ada.approval.config.security.*;
import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.service.AdminApiService;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.*;
import com.ada.approval.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TemporaryPasswordFlowTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatedPasswordsArePolicyCompliantAndIndependent() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String password = TemporaryPassword.generate();
            assertEquals(24, password.length());
            assertDoesNotThrow(() -> PasswordPolicy.validate(password));
            assertTrue(password.matches("[A-Za-z0-9_-]{24}"));
            assertTrue(values.add(password));
        }
    }

    @Test
    void createResponseContainsPasswordButPersistenceReceivesOnlyHash() throws Exception {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        IAccountService accountService = mock(IAccountService.class);
        var encoder = new BCryptPasswordEncoder(4);
        EmployeeServiceImpl business = new EmployeeServiceImpl(employees, accounts);
        ReflectionTestUtils.setField(business, "accountService", accountService);
        ReflectionTestUtils.setField(business, "passwordEncoder", encoder);
        when(employees.findMaxEmpNum()).thenReturn("000001");
        when(employees.saveAndFlush(any()))
                .thenAnswer(
                        i -> {
                            Employee e = i.getArgument(0);
                            e.setId(7);
                            return e;
                        });
        when(accountService.save(any())).thenReturn(true);
        var facade = new AdminApiService();
        var statuses = mock(EmployeeStatusRepository.class);
        EmployeeStatus active = new EmployeeStatus();
        active.setId(42);
        when(statuses.findAllByNameIgnoreCaseAndStatus("Active", 1))
                .thenReturn(Collections.singletonList(active));
        ReflectionTestUtils.setField(facade, "employeeService", business);
        ReflectionTestUtils.setField(facade, "statuses", statuses);
        ReflectionTestUtils.setField(facade, "departments", mock(DeptRepository.class));
        ReflectionTestUtils.setField(facade, "titles", mock(TitleCategoryRepository.class));
        CreateEmployeeRequest dto = new CreateEmployeeRequest();
        dto.setName("Person");
        dto.setEmail("person@example.test");
        dto.setMobile("555");
        EmployeeCreatedResponse response = facade.createEmployee(dto);
        assertNotNull(response.getTemporaryPassword());
        assertDoesNotThrow(() -> PasswordPolicy.validate(response.getTemporaryPassword()));
        verify(accountService)
                .save(
                        argThat(
                                a ->
                                        encoder.matches(
                                                        response.getTemporaryPassword(),
                                                        a.getPassword())
                                                && !response.getTemporaryPassword()
                                                        .equals(a.getPassword())
                                                && Integer.valueOf(7).equals(a.getEmpId())));
        verify(employees)
                .saveAndFlush(
                        argThat(
                                e ->
                                        Integer.valueOf(42).equals(e.getEmployStatusId())
                                                && "person@example.test".equals(e.getEmail())));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertTrue(json.writeValueAsString(response).contains("temporaryPassword"));
        assertFalse(response.toString().contains(response.getTemporaryPassword()));
        assertFalse(
                json.writeValueAsString(com.ada.approval.api.dto.ApiMapper.employee(new Employee()))
                        .contains("temporaryPassword"));
        assertFalse(
                Arrays.stream(Employee.class.getDeclaredFields())
                        .anyMatch(f -> f.getName().toLowerCase().contains("password")));
        assertFalse(
                Arrays.stream(Account.class.getDeclaredFields())
                        .anyMatch(f -> f.getName().toLowerCase().contains("temporary")));
    }

    @Test
    void createRequestHasNoInitialPasswordField() throws Exception {
        var json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .disable(
                                com.fasterxml.jackson.databind.DeserializationFeature
                                        .FAIL_ON_UNKNOWN_PROPERTIES);
        var dto =
                json.readValue(
                        "{\"name\":\"Person\",\"initialPassword\":\"client-selected-value\"}",
                        CreateEmployeeRequest.class);
        assertFalse(json.writeValueAsString(dto).contains("initialPassword"));
    }

    private AccountServiceImpl passwordService(
            AccountRepository repository, BCryptPasswordEncoder encoder) {
        var service = new AccountServiceImpl(repository);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        Account account = new Account();
        account.setId(7);
        account.setStatus(1);
        account.setUserName("self@example.test");
        account.setPassword(encoder.encode("old-safe-passphrase"));
        when(repository.findByUserNameAndStatus("self@example.test", 1)).thenReturn(account);
        when(repository.findById(7)).thenReturn(Optional.of(account));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                account, null, Collections.emptyList()));
        return service;
    }

    @Test
    void changesOnlyAuthenticatedAccountsPassword() {
        var repository = mock(AccountRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var service = passwordService(repository, encoder);
        service.updatePassword("old-safe-passphrase", "new-safe-passphrase", "new-safe-passphrase");
        verify(repository)
                .saveAndFlush(
                        argThat(
                                a ->
                                        Integer.valueOf(7).equals(a.getId())
                                                && encoder.matches(
                                                        "new-safe-passphrase", a.getPassword())));
        verify(repository).findByUserNameAndStatus("self@example.test", 1);
        verify(repository).findById(7);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void wrongCurrentPasswordNeverWrites() {
        var repository = mock(AccountRepository.class);
        var service = passwordService(repository, new BCryptPasswordEncoder(4));
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () ->
                        service.updatePassword(
                                "incorrect", "new-safe-passphrase", "new-safe-passphrase"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidNewPasswordNeverWrites() {
        var repository = mock(AccountRepository.class);
        var service = passwordService(repository, new BCryptPasswordEncoder(4));
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () -> service.updatePassword("old-safe-passphrase", "short", "short"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void mismatchAndReuseNeverWrite() {
        var repository = mock(AccountRepository.class);
        var service = passwordService(repository, new BCryptPasswordEncoder(4));
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () ->
                        service.updatePassword(
                                "old-safe-passphrase", "new-safe-passphrase", "different"));
        assertThrows(
                com.ada.approval.exception.ParamException.class,
                () ->
                        service.updatePassword(
                                "old-safe-passphrase",
                                "old-safe-passphrase",
                                "old-safe-passphrase"));
        verify(repository, never()).saveAndFlush(any());
    }
}
