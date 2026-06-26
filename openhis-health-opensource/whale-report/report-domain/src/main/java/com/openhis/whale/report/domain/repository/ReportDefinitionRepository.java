package com.openhis.whale.report.domain.repository;

import com.openhis.whale.report.domain.entity.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link ReportDefinition} 仓储：按租户与 ReportCode 查询，条件下推 DB。
 */
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    /**
     * 租户内按报表编码查单条未删除定义。
     *
     * @param tenantId   租户主键（含平台 tenant_id=0）
     * @param reportCode 报表编码
     */
    Optional<ReportDefinition> findByTenantIdAndReportCodeAndIsDeletedFalse(Long tenantId, String reportCode);

    /**
     * 列出租户下全部未删除定义，按名称升序（单租户规模通常 &lt; 200）。
     *
     * @param tenantId 租户主键
     */
    List<ReportDefinition> findByTenantIdAndIsDeletedFalseOrderByNameAsc(Long tenantId);

    /**
     * 按主键查未删除定义（编辑/发布前加载）。
     *
     * @param id 定义主键
     */
    Optional<ReportDefinition> findByIdAndIsDeletedFalse(Long id);
}
