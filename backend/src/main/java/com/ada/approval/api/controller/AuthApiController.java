package com.ada.approval.api.controller;

import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.entity.Account;
import com.ada.approval.service.IAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;

import java.util.stream.Collectors;

/**
 * Exposes session identity, CSRF bootstrap, logout and self-service password changes. Password
 * changes invalidate the calling session; authentication itself uses the security filter chain.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    @Autowired private IAccountService accounts;
    @Autowired private com.ada.approval.repository.EmployeeRepository employees;
    @Autowired private com.ada.approval.config.security.BackendAuthorization authorization;
    @Autowired private com.ada.approval.workflow.ApplicantWorkflow applicantWorkflow;

    // Reading the deferred token materializes it for the session before returning the bootstrap
    // DTO.
    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        CsrfResponse dto = new CsrfResponse();
        dto.setToken(token.getToken());
        dto.setHeaderName(token.getHeaderName());
        dto.setParameterName(token.getParameterName());
        return dto;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        Account account = (Account) authentication.getPrincipal();
        CurrentUserResponse dto = new CurrentUserResponse();
        dto.setAccountId(account.getId());
        dto.setEmployeeId(account.getEmpId());
        dto.setUsername(account.getUsername());
        String displayName = account.getUsername();
        if (account.getEmpId() != null) {
            var employee = employees.findById(account.getEmpId()).orElse(null);
            if (employee != null
                    && employee.getEmpName() != null
                    && !employee.getEmpName().isBlank()) {
                displayName = employee.getEmpName();
            }
        }
        dto.setDisplayName(displayName);
        dto.setAuthorities(
                authentication.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(Collectors.toList()));
        dto.setCanApplyForLeave(applicantWorkflow.canApply(account));
        dto.setCanAccessApprovals(authorization.canApprove(authentication));
        return dto;
    }

    @PostMapping("/logout")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        invalidate(request);
    }

    @PutMapping("/password")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody PasswordRequest dto, HttpServletRequest request) {
        accounts.updatePassword(
                dto.getCurrentPassword(), dto.getNewPassword(), dto.getConfirmation());
        invalidate(request);
    }

    private void invalidate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
