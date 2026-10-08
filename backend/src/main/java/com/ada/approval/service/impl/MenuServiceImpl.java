package com.ada.approval.service.impl;

import com.ada.approval.repository.MenuRepository;
import org.springframework.beans.BeanUtils;
import java.util.ArrayList;

import com.ada.approval.entity.Menu;
import com.ada.approval.service.IMenuService;
import com.ada.approval.service.IPermissionService;
import com.ada.approval.utils.AssertUtil;
import com.ada.approval.utils.PageResultUtil;
import com.ada.approval.service.projection.MenuGrantNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * Maintains the existing permission-resource catalog, including historical URL grant identities.
 * Leaf deletion removes its grants and soft-deletes the menu; active child menus block deletion.
 */
@Service
public class MenuServiceImpl extends JpaCrudService<Menu> implements IMenuService {
    private final MenuRepository menuRepository;

    public MenuServiceImpl(MenuRepository repository) {
        super(repository);
        this.menuRepository = repository;
    }

    @Autowired private IPermissionService permissionService;

    @Override
    public Map<String, Object> menuList() {
        List<Menu> menuList = new ArrayList<>();
        for (Object[] row : menuRepository.findMenuRows()) {
            Menu result = new Menu();
            BeanUtils.copyProperties((Menu) row[0], result);
            result.setParentName((String) row[1]);
            menuList.add(result);
        }
        // Pagination
        Map<String, Object> map = PageResultUtil.getResult((long) menuList.size(), menuList);
        return map;
    }

    @Override
    public void saveMenu(Menu menu) {
        // 1.Validate required fields
        AssertUtil.isTrue(StringUtils.isBlank(menu.getMenuName()), "Menu name is required");
        // 2.Validate hierarchy level
        Integer grade = menu.getGrade();
        // Validate submitted data
        AssertUtil.isTrue(
                null == grade || !(grade == 0 || grade == 1 || grade == 2), "Invalid menu level");
        // Menu names must be unique among siblings
        Menu temp = this.queryMenuByGradeAndMenuName(menu.getGrade(), menu.getMenuName());
        AssertUtil.isTrue(null != temp, "Menu name must be unique at this level");
        // Check for a parent menu
        if (grade != 0) {
            // Not a root-level record
            Integer parentId = menu.getParentId();
            Menu parentMenu = this.getById(parentId); // Find the parent menu by ID
            AssertUtil.isTrue(null == parentId || null == parentMenu, "Parent menu is required!");
        }
        // 3.Validate permission code
        AssertUtil.isTrue(StringUtils.isBlank(menu.getOptValue()), "Permission code is required");
        // Permission codes must be unique
        // Find by permission code
        temp = this.queryMenuByOptValue(menu.getOptValue());
        AssertUtil.isTrue(null != temp, "Permission codes must be unique!");
        // 4.Create the menu after validation
        menu.setIsValid(1); // New menus are enabled by default
        boolean save = this.save(menu);
        AssertUtil.isTrue(!save, "Unable to create menu");
    }

    @Override
    public void updateMenu(Menu menu) {
        // 1.Verify the menu to update exists
        Menu temp = this.getById(menu.getId());
        AssertUtil.isTrue(null == menu.getId() || null == temp, "Record not found");
        // 2.Validate required menu name
        AssertUtil.isTrue(StringUtils.isBlank(menu.getMenuName()), "Menu name is required");
        // 3.Check hierarchy level
        // 2.Validate hierarchy level
        Integer grade = menu.getGrade();
        // Validate submitted data
        AssertUtil.isTrue(
                null == grade || !(grade == 0 || grade == 1 || grade == 2), "Invalid menu level");
        // Menu names must be unique among siblings
        temp = this.queryMenuByGradeAndMenuName(menu.getGrade(), menu.getMenuName());
        if (null != temp) {
            AssertUtil.isTrue(
                    !(temp.getId().equals(menu.getId())), "Menu name already exists at this level");
        }
        // 3.Check for a parent menu
        if (grade != 0) {
            Integer parentId = menu.getParentId();
            AssertUtil.isTrue(
                    null == parentId || null == this.getById(parentId), "Parent menu is required");
        }
        // 4.Validate permission code
        AssertUtil.isTrue(StringUtils.isBlank(menu.getOptValue()), "Permission code is required");
        temp = this.queryMenuByOptValue(menu.getOptValue());
        if (null != temp) {
            AssertUtil.isTrue(
                    !(temp.getId().equals(menu.getId())), "Permission code already exists");
        }
        // 5.Update the record
        boolean b = this.updateById(menu);
        AssertUtil.isTrue(!b, "Unable to update menu");
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteMenu(Integer id) {
        // Find the menu by ID
        Menu menu = this.getById(id);
        AssertUtil.isTrue(null == menu, "Menu not found");
        // Delete only existing leaf records
        long count = menuRepository.countByParentIdAndIsValid(id, 1);
        AssertUtil.isTrue(count > 0, "Remove child menus before deleting this menu");
        // Zero deleted grants is normal for a leaf without assigned permissions.
        // Repository failures propagate and roll back the entire transaction.
        permissionService.removeByMenuId(id);
        // Soft deletion
        menu.setIsValid(0);
        boolean b = this.updateById(menu);
        AssertUtil.isTrue(!b, "Unable to delete menu");
    }

    @Override
    public List<MenuGrantNode> queryAllMenus(Integer roleId) {
        List<MenuGrantNode> treeVOS = new ArrayList<>();
        for (Object[] row : menuRepository.findTreeRows()) {
            MenuGrantNode tree = new MenuGrantNode();
            tree.setId((Integer) row[0]);
            tree.setPId((Integer) row[1]);
            tree.setName((String) row[2]);
            treeVOS.add(tree);
        }
        // Find the menus granted to this role
        List<Integer> roleHasMenuIds = permissionService.queryRoleHasAllMenuIdsByRoleId(roleId);
        if (!CollectionUtils.isEmpty(roleHasMenuIds)) {
            // Iterate through the collection
            for (MenuGrantNode treeVO : treeVOS) {
                if (roleHasMenuIds.contains(treeVO.getId())) { // Mark this permission as granted
                    treeVO.setChecked(true);
                }
            }
        }
        return treeVOS;
    }

    /**
     * Find the menu by permission code
     *
     * @param optValue
     * @return
     */
    private Menu queryMenuByOptValue(String optValue) {
        Menu one = menuRepository.findByOptValueAndIsValid(optValue, 1);
        return one;
    }

    private Menu queryMenuByGradeAndMenuName(Integer grade, String menuName) {
        Menu menu = menuRepository.findByGradeAndMenuNameAndIsValid(grade, menuName, 1);
        return menu;
    }
}
