/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.repository;

import com.zxplat.plugin.chs.domain.audit.entity.YbDetailAuditHitLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 明细审核违规明细仓储（仅本聚合）。
 */
public interface YbDetailAuditHitLineRepository extends JpaRepository<YbDetailAuditHitLine, Long> {

    /**
     * 按租户 + jrd_id 查找未删除明细（幂等落库）。
     *
     * @param tenantId 租户
     * @param jrdId    平台违规明细 ID
     * @return 已有记录
     */
    Optional<YbDetailAuditHitLine> findByTenantIdAndJrdIdAndIsDeletedFalse(Long tenantId, String jrdId);

    /**
     * 按违规头 jr_id 列出明细行（统计页抽屉）。
     *
     * @param tenantId 租户
     * @param jrId     违规头 ID
     * @return 明细行
     */
    List<YbDetailAuditHitLine> findByTenantIdAndJrIdAndIsDeletedFalseOrderByIdAsc(
            Long tenantId, String jrId);
}
