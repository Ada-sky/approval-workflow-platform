package com.ada.approval.service.impl;

import com.ada.approval.entity.Permission;
import com.ada.approval.service.IPermissionService;
import com.ada.approval.repository.PermissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Maintains numeric permission grants used by backend authorization. Grant evaluation depends on
 * enabled accounts and valid role/menu records, not frontend visibility.
 */
@Service
public class PermissionServiceImpl extends JpaCrudService<Permission>
        implements IPermissionService {
    private final PermissionRepository permissionRepository;

    public PermissionServiceImpl(PermissionRepository repository) {
        super(repository);
        this.permissionRepository = repository;
    }

    @Override
    public long countByRoleId(Integer roleId) {
        return permissionRepository.countByRoleId(roleId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean removeByRoleId(Integer roleId) {
        return permissionRepository.deleteForRole(roleId) > 0;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean removeByMenuId(Integer menuId) {
        return permissionRepository.deleteForMenu(menuId) > 0;
    }

    @Override
    public List<Integer> queryRoleHasAllMenuIdsByRoleId(Integer roleId) {
        List<Integer> list = permissionRepository.findMenuIds(roleId);
        return list;
    }

    @Override
    public List<String> findAuthorityByUserName(String username) {
        List<String> list = permissionRepository.findAuthorities(username);
        return list;
    }
}
