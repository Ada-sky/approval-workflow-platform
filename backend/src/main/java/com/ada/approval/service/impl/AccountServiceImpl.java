package com.ada.approval.service.impl;

import com.ada.approval.entity.Account;
import com.ada.approval.service.IAccountService;
import com.ada.approval.repository.AccountRepository;
import com.ada.approval.utils.AssertUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Resolves login accounts and validates self-service password changes. Passwords are BCrypt-encoded
 * before persistence; callers and session refresh enforce invalidation after changes.
 */
@Service
public class AccountServiceImpl extends JpaCrudService<Account> implements IAccountService {
    private final AccountRepository accountRepository;

    public AccountServiceImpl(AccountRepository repository) {
        super(repository);
        this.accountRepository = repository;
    }

    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public Account findAccountByUserName(String userName) {
        // Look up the unique account by username
        Account account = accountRepository.findByUserNameAndStatus(userName, 1);
        return account;
    }

    @Override
    public Account findAccountByEmpId(Integer empId) {
        return accountRepository.findByEmpId(empId);
    }

    @Override
    @org.springframework.security.access.prepost.PreAuthorize("isFullyAuthenticated()")
    public void updatePassword(String oldPassowrd, String newPassword, String repeatPassword) {
        // 1.Validate required passwords
        AssertUtil.isTrue(StringUtils.isBlank(oldPassowrd), "Current password is required");
        AssertUtil.isTrue(StringUtils.isBlank(newPassword), "New password is required");
        AssertUtil.isTrue(StringUtils.isBlank(repeatPassword), "Password confirmation is required");
        // 2.Verify the supplied current password
        // 2-1 Retrieve the password for the account authenticated by Spring Security
        //    Resolve the authenticated account through Spring Security
        String name =
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName(); // Get the authenticated username
        //   Find the account by username
        Account account = this.findAccountByUserName(name);
        // True indicates a match; false indicates a mismatch
        boolean matches = passwordEncoder.matches(oldPassowrd, account.getPassword());
        // Validation fails when the condition is true
        AssertUtil.isTrue(!matches, "Current password is incorrect");
        // 3.Verify password confirmation matches
        AssertUtil.isTrue(!newPassword.equals(repeatPassword), "New passwords do not match");
        // 4.Require the new password to differ from the current password
        AssertUtil.isTrue(
                passwordEncoder.matches(newPassword, account.getPassword()),
                "New password must differ from the current password");
        com.ada.approval.config.security.PasswordPolicy.validate(newPassword);
        // 5.Update the password after validation
        account.setPassword(
                passwordEncoder.encode(newPassword)); // Encode the password before saving
        // Update the record
        boolean updated = this.updateById(account);
        AssertUtil.isTrue(!updated, "Unable to change password");
    }
}
