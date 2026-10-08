package com.ada.approval.service;

import com.ada.approval.entity.Menu;
import com.ada.approval.service.projection.MenuGrantNode;

import java.util.List;
import java.util.Map;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-19
 */
public interface IMenuService extends CrudService<Menu> {
    /**
     * List menus
     *
     * @return
     */
    Map<String, Object> menuList();

    /**
     * Create menu
     *
     * @param menu
     */
    void saveMenu(Menu menu);

    /**
     * Update menu
     *
     * @param menu
     */
    void updateMenu(Menu menu);

    /**
     * Delete menu
     *
     * @param id
     */
    void deleteMenu(Integer id);

    /**
     * List menus
     *
     * @param roleId
     * @return
     */
    List<MenuGrantNode> queryAllMenus(Integer roleId);
}
