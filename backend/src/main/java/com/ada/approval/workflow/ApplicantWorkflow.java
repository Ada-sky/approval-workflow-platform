package com.ada.approval.workflow;

import com.ada.approval.api.error.ApiException;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Resolves organizational applicant identity separately from numeric security permissions.
 * Snapshots routing variables at submission and validates approvers, including the prohibition on
 * self-approval.
 */
@Service
public class ApplicantWorkflow {
    public enum Identity {
        EMPLOYEE,
        DEPARTMENT_MANAGER,
        GENERAL_MANAGER,
        HR,
        SYSTEM_ADMIN
    }

    public static final String IDENTITY_VARIABLE = "applicantWorkflowIdentity";
    private final EmployeeRepository employees;
    private final DeptRepository departments;
    private final TitleCategoryRepository titles;
    private final AccountRepository accounts;
    private final com.ada.approval.config.security.BackendAuthorization authorization;

    public ApplicantWorkflow(
            EmployeeRepository employees,
            DeptRepository departments,
            TitleCategoryRepository titles,
            AccountRepository accounts,
            com.ada.approval.config.security.BackendAuthorization authorization) {
        this.employees = employees;
        this.departments = departments;
        this.titles = titles;
        this.accounts = accounts;
        this.authorization = authorization;
    }

    public Identity identity(Account account) {
        if (account == null || !account.isEnabled())
            throw new ApiException(403, "An active employee account is required");
        if (account.getEmpId() == null) return Identity.SYSTEM_ADMIN;
        Employee employee = employees.findByIdAndStatus(account.getEmpId(), 1);
        if (employee == null) throw new ApiException(403, "An active employee account is required");
        TitleCategory title =
                employee.getTitleCategoryId() == null
                        ? null
                        : titles.findById(employee.getTitleCategoryId()).orElse(null);
        String name = title == null ? "" : title.getTitleName();
        if ("Administrator".equals(name)) return Identity.SYSTEM_ADMIN;
        if (WorkflowTitleNames.GENERAL_MANAGER.equals(WorkflowTitleNames.display(name)))
            return Identity.GENERAL_MANAGER;
        if (WorkflowTitleNames.HR.equals(WorkflowTitleNames.display(name))) return Identity.HR;
        if (departments.existsByManagerIdAndStatus(employee.getId(), 1))
            return Identity.DEPARTMENT_MANAGER;
        return Identity.EMPLOYEE;
    }

    public boolean canApply(Account account) {
        try {
            return identity(account) != Identity.SYSTEM_ADMIN;
        } catch (ApiException invalid) {
            return false;
        }
    }

    /**
     * Defines the applicant-aware approval route. Employees require Manager then HR for at most
     * three days, with General Manager added only above three. Managers require General Manager
     * then HR; General Managers require HR; HR requires General Manager. System-only administrators
     * cannot submit leave.
     */
    public static List<String> stages(Identity identity, int days) {
        return switch (identity) {
            case EMPLOYEE ->
                    days > 3
                            ? List.of("DEPARTMENT_MANAGER", "GENERAL_MANAGER", "HR")
                            : List.of("DEPARTMENT_MANAGER", "HR");
            case DEPARTMENT_MANAGER -> List.of("GENERAL_MANAGER", "HR");
            case GENERAL_MANAGER -> List.of("HR");
            case HR -> List.of("GENERAL_MANAGER");
            case SYSTEM_ADMIN ->
                    throw new ApiException(
                            403, "System administration accounts cannot apply for leave");
        };
    }

    /**
     * Snapshots applicant identity and required approvers before a process starts. Every selected
     * approver must have an eligible linked account, the workbench grant and a different identity
     * from the applicant.
     */
    public Map<String, Object> variables(Account applicant, int days) {
        Identity identity = identity(applicant);
        List<String> route = stages(identity, days);
        Map<String, Object> variables = new HashMap<>();
        variables.put(IDENTITY_VARIABLE, identity.name());
        variables.put("day", days);
        variables.put("user", applicant.getUsername());
        if (route.contains("DEPARTMENT_MANAGER"))
            variables.put(
                    "manager",
                    approver(employees.findDepartmentManager(applicant.getEmpId()), applicant));
        if (route.contains("GENERAL_MANAGER")) {
            List<Employee> bosses =
                    employees.findAllByJobTitles(
                            com.ada.approval.workflow.WorkflowTitleNames.generalManagerNames());
            if (bosses == null || bosses.size() != 1)
                throw new ApiException(
                        409, "Exactly one active General Manager must be configured");
            variables.put("boss", approver(bosses.get(0), applicant));
        }
        if (route.contains("HR")) {
            List<Employee> hrs =
                    employees.findAllByJobTitles(
                            com.ada.approval.workflow.WorkflowTitleNames.hrNames());
            if (hrs == null || hrs.isEmpty())
                throw new ApiException(409, "HR approvers must be configured");
            variables.put(
                    "hrs",
                    String.join(
                            ",",
                            hrs.stream().map(hr -> approver(hr, applicant)).distinct().toList()));
        }
        return variables;
    }

    private String approver(Employee employee, Account applicant) {
        if (employee == null || !Objects.equals(employee.getStatus(), 1))
            throw new ApiException(409, "An active required approver must be configured");
        Account account = accounts.findByEmpId(employee.getId());
        if (account == null
                || !account.isEnabled()
                || account.getUsername() == null
                || account.getUsername().isBlank()
                || Objects.equals(account.getId(), applicant.getId()))
            throw new ApiException(409, "A distinct active required approver must be configured");
        if (!authorization.canApprove(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        account, null, Collections.emptyList())))
            throw new ApiException(409, "A required approver lacks approval access");
        return account.getUsername();
    }
}
