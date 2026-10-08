package com.ada.approval.service;

import com.ada.approval.entity.AccountRole;
import com.ada.approval.query.AccountRoleQuery;

import java.util.Map;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-19
 */
public interface IAccountRoleService extends CrudService<AccountRole> {
    /**
     * Assign a role to an employee
     *
     * @param roleId
     * @param eId
     */
    void saveAccountRole(Integer roleId, Integer eId);

    /**
     * List users assigned to the role
     *
     * @param accountRoleQuery
     * @return
     */
    Map<String, Object> accountRoleList(AccountRoleQuery accountRoleQuery);

    /**
     * Remove an employee role assignment
     *
     * @param roleId
     * @param accountId
     */
    void cancelRoleToUser(Integer roleId, Integer accountId);
}
