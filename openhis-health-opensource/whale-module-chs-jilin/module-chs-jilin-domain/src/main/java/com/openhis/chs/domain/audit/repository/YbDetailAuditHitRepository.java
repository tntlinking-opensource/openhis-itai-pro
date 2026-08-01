/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.repository;

import com.openhis.chs.domain.audit.entity.YbDetailAuditHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 明细审核违规头仓储（仅本聚合）。
 */
public interface YbDetailAuditHitRepository
    extends JpaRepository<YbDetailAuditHit, Long>, JpaSpecificationExecutor<YbDetailAuditHit> {

    /**
     * 按租户 + jr_id 查找未删除违规头（幂等落库）。
     *
     * @param tenantId 租户
     * @param jrId     平台违规头 ID
     * @return 已有记录
     */
    Optional<YbDetailAuditHit> findByTenantIdAndJrIdAndIsDeletedFalse(Long tenantId, String jrId);
}
