package com.openhis.whale.report.domain.repository;

import com.openhis.whale.report.domain.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    Optional<ReportTemplate> findFirstByTenantIdAndCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
            Long tenantId, String code, String status);

    List<ReportTemplate> findByTenantIdAndIsDeletedFalseOrderByCodeAscVersionDesc(Long tenantId);

    Optional<ReportTemplate> findByIdAndIsDeletedFalse(Long id);

    Optional<ReportTemplate> findFirstByTenantIdAndCodeAndIsDeletedFalseOrderByVersionDesc(
            Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIsDeletedFalse(Long tenantId, String code);
}
