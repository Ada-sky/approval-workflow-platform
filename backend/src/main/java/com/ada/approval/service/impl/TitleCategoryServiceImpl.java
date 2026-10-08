package com.ada.approval.service.impl;

import com.ada.approval.repository.TitleCategoryRepository;
import org.springframework.beans.BeanUtils;
import java.util.ArrayList;

import com.ada.approval.entity.TitleCategory;
import com.ada.approval.service.ITitleCategoryService;
import com.ada.approval.utils.AssertUtil;
import com.ada.approval.utils.PageResultUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service implementation
 *
 * @author Yan Min
 * @since 2023-12-18
 */
@Service
public class TitleCategoryServiceImpl extends JpaCrudService<TitleCategory>
        implements ITitleCategoryService {
    private final TitleCategoryRepository titleCategoryRepository;

    public TitleCategoryServiceImpl(TitleCategoryRepository repository) {
        super(repository);
        this.titleCategoryRepository = repository;
    }

    @Override
    public Map<String, Object> titleCategoryList() {
        List<TitleCategory> list = new ArrayList<>();
        for (TitleCategory category : titleCategoryRepository.findActiveCategories()) {
            TitleCategory result = new TitleCategory();
            BeanUtils.copyProperties(category, result, "status");
            // parent_title_name had no mapped property in the original entity.
            list.add(result);
        }
        Map<String, Object> map = PageResultUtil.getResult((long) list.size(), list);
        return map;
    }

    @Override
    public void savetTitleCategory(TitleCategory titleCategory) {
        // 1.Validate required fields
        checkTtitleCategoryParams(titleCategory.getTitleName(), titleCategory.getTitleNum());
        // 2.Validate job title number uniqueness
        TitleCategory temp = findTtitleCategoryByTitleNum(titleCategory.getTitleNum());
        AssertUtil.isTrue(null != temp, "Job title number must be unique!");
        // 3.Job title names must be unique at the same level
        AssertUtil.isTrue(
                titleCategoryRepository.countSiblingName(
                                titleCategory.getTitleName(),
                                titleCategory.getLevel(),
                                titleCategory.getParentId(),
                                null)
                        > 0,
                "Job title name must be unique at this level!");
        // 4.Create job title
        titleCategory.setStatus(1); // Enabled by default
        boolean save = this.save(titleCategory);
        AssertUtil.isTrue(!save, "Unable to create job title");
    }

    @Override
    public void updateTitleCategory(TitleCategory titleCategory) {
        // 1.Verify the job title to update exists
        TitleCategory temp = this.getById(titleCategory.getId());
        AssertUtil.isTrue(null == temp, "Job title not found");
        Integer parentId =
                titleCategory.getParentId() == null
                        ? temp.getParentId()
                        : titleCategory.getParentId();
        Integer level =
                titleCategory.getLevel() == null ? temp.getLevel() : titleCategory.getLevel();
        // 2.Validate required fields
        checkTtitleCategoryParams(titleCategory.getTitleName(), titleCategory.getTitleNum());
        // 3.Validate job title number uniqueness, excluding the current record
        temp = this.findTtitleCategoryByTitleNum(titleCategory.getTitleNum());
        AssertUtil.isTrue(
                null != temp && !java.util.Objects.equals(temp.getId(), titleCategory.getId()),
                "Job title number must be unique");
        // 4.Validate job title name uniqueness at the same level, excluding the current record
        AssertUtil.isTrue(
                titleCategoryRepository.countSiblingName(
                                titleCategory.getTitleName(),
                                level,
                                parentId,
                                titleCategory.getId())
                        > 0,
                "Job title name already exists at this level!");
        // 5.Update the record
        boolean b = this.updateById(titleCategory);
        AssertUtil.isTrue(!b, "Unable to update job title!");
    }

    @Override
    public void deleteTitleCategory(Integer id) {
        // Find the record by ID
        TitleCategory titleCategory = this.getById(id);
        // Verify the record exists before deletion
        AssertUtil.isTrue(null == titleCategory, "Record not found");
        // Update the soft-delete marker
        titleCategory.setStatus(0); // 0 indicates deletion
        // Update the record
        boolean b = this.updateById(titleCategory);
        AssertUtil.isTrue(!b, "Unable to delete job title");
    }

    @Override
    public List<TitleCategory> queryAllTitleCategories() {
        List<TitleCategory> list = repository.findAll();
        return list;
    }

    /**
     * Find the job title by name and level
     *
     * @param titleName Job title name
     * @param level Level
     * @return Job title object
     */
    private TitleCategory findTtitleCategoryByTitleNameAndLevel(String titleName, Integer level) {
        TitleCategory titleCategory =
                titleCategoryRepository.findActiveByNameAndLevel(titleName, level);
        return titleCategory;
    }

    /**
     * Find the job title by number
     *
     * @param titleNum Job title number
     * @return Job title object
     */
    private TitleCategory findTtitleCategoryByTitleNum(String titleNum) {
        TitleCategory titleCategory = titleCategoryRepository.findByTitleNumAndStatus(titleNum, 1);
        return titleCategory;
    }

    /**
     * Validate required parameters
     *
     * @param titleName Job title name
     * @param titleNum Job title number
     */
    private void checkTtitleCategoryParams(String titleName, String titleNum) {
        AssertUtil.isTrue(StringUtils.isBlank(titleName), "Job title name is required");
        AssertUtil.isTrue(StringUtils.isBlank(titleNum), "Job title number is required");
    }
}
