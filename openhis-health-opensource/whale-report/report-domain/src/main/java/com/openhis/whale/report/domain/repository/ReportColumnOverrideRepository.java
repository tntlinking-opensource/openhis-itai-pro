package com.openhis.whale.report.domain.repository;

import com.openhis.whale.report.domain.entity.ReportColumnOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link ReportColumnOverride} 仓储：用户/机构级列定制，每维度至多保留一条有效行。
 */
public interface ReportColumnOverrideRepository extends JpaRepository<ReportColumnOverride, Long> {

    /**
     * 查用户级 override（取最近更新的一条）。
     *
     * @param tenantId   租户主键
     * @param reportCode 报表编码
     * @param userId     用户主键
     */
    Optional<ReportColumnOverride> findFirstByTenantIdAndReportCodeAndUserIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            Long tenantId, String reportCode, Long userId);

    /**
     * 查机构级 override（user_id 为空，取最近更新的一条）。
     *
     * @param tenantId       租户主键
     * @param reportCode     报表编码
     * @param organizationId 机构主键
     */
    Optional<ReportColumnOverride> findFirstByTenantIdAndReportCodeAndOrganizationIdAndUserIdIsNullAndIsDeletedFalseOrderByUpdatedAtDesc(
            Long tenantId, String reportCode, Long organizationId);
}
