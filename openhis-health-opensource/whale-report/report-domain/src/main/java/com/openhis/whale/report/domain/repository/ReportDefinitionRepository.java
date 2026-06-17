package com.openhis.whale.report.domain.repository;

import com.openhis.whale.report.domain.entity.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    Optional<ReportDefinition> findByTenantIdAndReportCodeAndIsDeletedFalse(Long tenantId, String reportCode);

    List<ReportDefinition> findByTenantIdAndIsDeletedFalseOrderByNameAsc(Long tenantId);

    List<ReportDefinition> findByTenantIdAndStatusAndIsDeletedFalseOrderByNameAsc(Long tenantId, String status);

    Optional<ReportDefinition> findByIdAndIsDeletedFalse(Long id);
}
