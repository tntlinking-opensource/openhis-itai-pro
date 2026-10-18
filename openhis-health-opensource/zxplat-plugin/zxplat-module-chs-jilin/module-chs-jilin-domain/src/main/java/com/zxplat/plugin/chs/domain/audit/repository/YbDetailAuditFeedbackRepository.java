/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.repository;

import com.zxplat.plugin.chs.domain.audit.entity.YbDetailAuditFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 明细审核 3103 反馈仓储（仅本聚合）。
 */
public interface YbDetailAuditFeedbackRepository extends JpaRepository<YbDetailAuditFeedback, Long> {

    /**
     * 按违规头 jr_id 批量取反馈（最新在前，供统计页回填末次处置）。
     *
     * @param tenantId 租户
     * @param jrIds    违规头 ID 集合
     * @return 反馈行
     */
    List<YbDetailAuditFeedback> findByTenantIdAndJrIdInAndIsDeletedFalseOrderByCreatedAtDesc(
            Long tenantId, Collection<String> jrIds);
}
