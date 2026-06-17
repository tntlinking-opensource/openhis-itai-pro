package com.openhis.whale.report.domain.repository;

import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrintElementDefRepository extends JpaRepository<PrintElementDef, Long> {

    List<PrintElementDef> findByTenantIdAndPackCodeAndLayoutModeAndIsDeletedFalseOrderBySortOrderAsc(
            Long tenantId, String packCode, String layoutMode);

    List<PrintElementDef> findByTenantIdAndPackCodeAndIsDeletedFalseOrderBySortOrderAsc(
            Long tenantId, String packCode);
}
