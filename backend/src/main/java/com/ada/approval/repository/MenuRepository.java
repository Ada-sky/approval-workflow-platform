package com.ada.approval.repository;

import com.ada.approval.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Queries preserve the existing scalar foreign keys and legacy filters. */
public interface MenuRepository extends JpaRepository<Menu, Integer> {
    org.springframework.data.domain.Page<Menu> findByIsValid(
            Integer valid, org.springframework.data.domain.Pageable pageable);

    Menu findByOptValueAndIsValid(String optValue, Integer isValid);

    Menu findByGradeAndMenuNameAndIsValid(Integer grade, String menuName, Integer isValid);

    long countByParentIdAndIsValid(Integer parentId, Integer isValid);

    @Query(
            "select m, coalesce(parent.menuName, '-') from Menu m "
                    + "left join Menu parent on parent.id = m.parentId where m.isValid = 1")
    List<Object[]> findMenuRows();

    @Query("select m.id, coalesce(m.parentId, 0), m.menuName from Menu m where m.isValid = 1")
    List<Object[]> findTreeRows();
}
