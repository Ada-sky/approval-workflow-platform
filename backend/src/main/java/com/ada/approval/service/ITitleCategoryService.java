package com.ada.approval.service;

import com.ada.approval.entity.TitleCategory;

import java.util.List;
import java.util.Map;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-18
 */
public interface ITitleCategoryService extends CrudService<TitleCategory> {
    /**
     * List job title categories
     *
     * @return
     */
    public Map<String, Object> titleCategoryList();

    /**
     * Create job title
     *
     * @param titleCategory Job title to create
     */
    void savetTitleCategory(TitleCategory titleCategory);

    /**
     * Update job title
     *
     * @param titleCategory Job title to update
     */
    void updateTitleCategory(TitleCategory titleCategory);

    /** Delete job title */
    void deleteTitleCategory(Integer id);

    /** List job titles for employee creation */
    List<TitleCategory> queryAllTitleCategories();
}
