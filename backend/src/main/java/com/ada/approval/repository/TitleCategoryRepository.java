package com.ada.approval.repository;

import com.ada.approval.entity.TitleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Queries preserve the existing scalar foreign keys and legacy filters. */
public interface TitleCategoryRepository extends JpaRepository<TitleCategory, Integer> {
    org.springframework.data.domain.Page<TitleCategory>
            findByStatusAndTitleNameContainingIgnoreCase(
                    Integer status, String name, org.springframework.data.domain.Pageable pageable);

    TitleCategory findByTitleNumAndStatus(String titleNum, Integer status);

    @Query(
            "select t from TitleCategory t where t.titleName = :name and t.level = :level and t.status = 1")
    TitleCategory findActiveByNameAndLevel(
            @Param("name") String name, @Param("level") Integer level);

    @Query(
            "select count(t) from TitleCategory t where t.status = 1 and t.titleName = :name "
                    + "and (t.level = :level or (t.level is null and :level is null)) "
                    + "and (t.parentId = :parentId or (t.parentId is null and :parentId is null)) "
                    + "and (:excludedId is null or t.id <> :excludedId)")
    long countSiblingName(
            @Param("name") String name,
            @Param("level") Integer level,
            @Param("parentId") Integer parentId,
            @Param("excludedId") Integer excludedId);

    @Query(
            "select t from TitleCategory t left join TitleCategory parent on parent.id = t.parentId where t.status = 1")
    List<TitleCategory> findActiveCategories();
}
