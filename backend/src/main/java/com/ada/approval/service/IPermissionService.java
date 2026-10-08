package com.ada.approval.service;

import com.ada.approval.entity.Permission;

import java.util.List;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-19
 */
public interface IPermissionService extends CrudService<Permission> {
    long countByRoleId(Integer roleId);

    boolean removeByRoleId(Integer roleId);

    boolean removeByMenuId(Integer menuId);

    List<Integer> queryRoleHasAllMenuIdsByRoleId(Integer roleId);

    /**
     * List granted user permission codes
     *
     * @param username
     * @return
     */
    List<String> findAuthorityByUserName(String username);
}
