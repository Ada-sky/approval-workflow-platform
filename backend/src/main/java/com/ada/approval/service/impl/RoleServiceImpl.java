package com.ada.approval.service.impl;

import com.ada.approval.entity.Menu;
import com.ada.approval.entity.Permission;
import com.ada.approval.entity.Role;
import com.ada.approval.query.RoleQuery;
import com.ada.approval.service.IMenuService;
import com.ada.approval.service.IPermissionService;
import com.ada.approval.service.IRoleService;
import com.ada.approval.repository.RoleRepository;
import com.ada.approval.utils.AssertUtil;
import com.ada.approval.utils.PageResultUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service implementation
 *
 * @author Yan Min
 * @since 2023-12-19
 */
@Service
public class RoleServiceImpl extends JpaCrudService<Role> implements IRoleService {
    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository repository) {
        super(repository);
        this.roleRepository = repository;
    }

    @Autowired private IPermissionService permissionService;
    @Autowired private IMenuService menuService;

    @Override
    public Map<String, Object> findRoles(RoleQuery roleQuery) {
        String name =
                roleQuery != null && StringUtils.isNotEmpty(roleQuery.getRoleName())
                        ? roleQuery.getRoleName()
                        : null;
        Integer status = roleQuery == null ? null : roleQuery.getStatus();
        List<Role> roles = roleRepository.search(name, status);
        return PageResultUtil.getResult((long) roles.size(), roles);
    }

    @Override
    public void saveRole(Role role) {
        // Validate required fields before creation
        AssertUtil.isTrue(StringUtils.isBlank(role.getRoleName()), "Role name is required");
        // Role names must be unique
        Role temp = this.findRoleByName(role.getRoleName());
        AssertUtil.isTrue(null != temp, "Role name already exists");
        // Create role
        boolean saved = this.save(role);
        AssertUtil.isTrue(!saved, "Unable to create role");
    }

    /**
     * Find the role by name
     *
     * @param roleName
     * @return
     */
    private Role findRoleByName(String roleName) {
        Role role = roleRepository.findByRoleNameAndStatus(roleName, 1);
        return role;
    }

    @Override
    public void updateRole(Role role) {
        // Validate required fields before creation
        AssertUtil.isTrue(StringUtils.isBlank(role.getRoleName()), "Role name is required");
        // Role names must be unique
        Role temp = this.findRoleByName(role.getRoleName());
        AssertUtil.isTrue(
                null != temp && !(role.getId().equals(temp.getId())), "Role name already exists");
        // Update the record
        boolean b = this.updateById(role);
        AssertUtil.isTrue(!b, "Unable to update role");
    }

    @Override
    public void addGrant(Integer[] mids, Integer roleId) {
        // Verify the role exists before granting permissions
        Role temp = this.getById(roleId);
        AssertUtil.isTrue(null == roleId || null == temp, "Role not found");
        // Check whether the role already has grants
        long count = this.permissionService.countByRoleId(roleId);
        if (count > 0) { // Existing permission grants
            // Remove existing grants before replacement
            boolean flag = this.permissionService.removeByRoleId(roleId);
            AssertUtil.isTrue(!flag, "Unable to assign permissions");
        }
        // Replace existing grants with the submitted set
        if (null != mids && mids.length > 0) {
            ArrayList<Permission> permissions = new ArrayList<>();
            for (Integer mid : mids) {
                Permission permission = new Permission();
                permission.setMenuId(mid);
                permission.setRoleId(roleId);
                // Set permission grants
                permission.setAclValue(this.menuService.getById(mid).getOptValue());
                // Add to collection
                permissions.add(permission);
            }
            // Save in a batch
            boolean b = this.permissionService.saveBatch(permissions);
            AssertUtil.isTrue(!b, "Unable to grant role permissions");
        }
    }
}
