package com.ada.approval.service;

import com.ada.approval.entity.Role;
import com.ada.approval.query.RoleQuery;

import java.util.Map;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-19
 */
public interface IRoleService extends CrudService<Role> {
    /**
     * List roles
     *
     * @return Role list
     */
    Map<String, Object> findRoles(RoleQuery roleQuery);

    /**
     * Create role
     *
     * @param role
     */
    void saveRole(Role role);

    /**
     * Update role
     *
     * @param role
     */
    void updateRole(Role role);

    /**
     * Grant permissions
     *
     * @param mids
     * @param roleId
     */
    void addGrant(Integer[] mids, Integer roleId);
}
