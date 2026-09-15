/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import com.openhis.chs.domain.inventory.entity.YbInvUploadTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 进销存上报追溯码明细仓储。
 */
public interface YbInvUploadTraceRepository extends JpaRepository<YbInvUploadTrace, Long> {

    /**
     * 按上报流水主键查询追溯码明细，按 id 升序。
     *
     * @param uploadId 上报流水主键
     * @return 追溯码明细列表
     */
    List<YbInvUploadTrace> findAllByUploadIdOrderByIdAsc(Long uploadId);

    /**
     * 查租户下未软删的同码追溯明细（唯一索引 {@code uk_yb_inv_upload_trace_codg} 冲突前先软删用）。
     *
     * @param tenantId     租户主键
     * @param drugTracCodg 药品追溯码
     * @return 未删明细；可多条历史脏数据时全返回
     */
    List<YbInvUploadTrace> findAllByTenantIdAndDrugTracCodgAndIsDeletedFalse(
        Long tenantId,
        String drugTracCodg
    );
}
