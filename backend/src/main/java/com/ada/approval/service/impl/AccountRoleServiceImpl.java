package com.ada.approval.service.impl;

import com.ada.approval.repository.AccountRoleRepository;
import com.ada.approval.entity.Account;
import com.ada.approval.service.IAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;

import com.ada.approval.entity.AccountRole;
import com.ada.approval.query.AccountRoleQuery;
import com.ada.approval.service.IAccountRoleService;
import com.ada.approval.utils.AssertUtil;
import com.ada.approval.utils.PageResultUtil;
import com.ada.approval.service.projection.AccountRoleRow;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Maintains numeric role assignments for linked accounts. Employee IDs supplied by administration
 * are resolved to Account.id before storing account-role grants.
 */
@Service
public class AccountRoleServiceImpl extends JpaCrudService<AccountRole>
        implements IAccountRoleService {
    private final AccountRoleRepository accountRoleRepository;

    public AccountRoleServiceImpl(AccountRoleRepository repository) {
        super(repository);
        this.accountRoleRepository = repository;
    }

    @Autowired private IAccountService accountService;

    @Override
    public void saveAccountRole(Integer roleId, Integer eId) {
        // Validate business rules in the service
        AccountRole accountRole = new AccountRole();
        AssertUtil.isTrue(eId == null, "Employee is required");
        Account account = accountService.findAccountByEmpId(eId);
        AssertUtil.isTrue(account == null, "Employee has no linked account");
        accountRole.setAccountId(account.getId());
        accountRole.setRoleId(roleId);
        boolean save = this.save(accountRole);
        AssertUtil.isTrue(!save, "Unable to assign employee role");
    }

    @Override
    public Map<String, Object> accountRoleList(AccountRoleQuery accountRoleQuery) {
        List<AccountRoleRow> records = new ArrayList<>();
        String name =
                StringUtils.isEmpty(accountRoleQuery.getEmpName())
                        ? null
                        : accountRoleQuery.getEmpName();
        String number =
                StringUtils.isEmpty(accountRoleQuery.getEmpNum())
                        ? null
                        : accountRoleQuery.getEmpNum();
        for (Object[] row :
                accountRoleRepository.findAssignmentRows(
                        accountRoleQuery.getRoleId(), name, number)) {
            AccountRoleRow vo = new AccountRoleRow();
            vo.setAid((Integer) row[0]);
            vo.setUserName((String) row[1]);
            vo.setEmpNum((String) row[2]);
            vo.setEmpName((String) row[3]);
            records.add(vo);
        }
        // Preserve the current un-intercepted MyBatis list/total behavior.
        return PageResultUtil.getResult(0L, records);
    }

    @Override
    public void cancelRoleToUser(Integer roleId, Integer accountId) {
        // Find the assignment by role and account IDs
        AccountRole accountRole = accountRoleRepository.findByAccountIdAndRoleId(accountId, roleId);
        AssertUtil.isTrue(null == accountRole, "Role assignment not found");
        // Delete
        boolean b = accountRoleRepository.deleteAssignment(accountRole.getId()) > 0;
        AssertUtil.isTrue(!b, "Unable to remove role assignment");
    }
}
