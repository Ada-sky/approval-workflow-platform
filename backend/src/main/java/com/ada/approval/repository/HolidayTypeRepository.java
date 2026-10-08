package com.ada.approval.repository;

import com.ada.approval.entity.HolidayType;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the existing table; foreign keys remain scalar IDs. */
public interface HolidayTypeRepository extends JpaRepository<HolidayType, Integer> {

    org.springframework.data.domain.Page<HolidayType> findByHolidayTypeContainingIgnoreCase(
            String name, org.springframework.data.domain.Pageable pageable);

    boolean existsByHolidayTypeIgnoreCase(String name);

    boolean existsByHolidayTypeIgnoreCaseAndIdNot(String name, Integer id);
}
