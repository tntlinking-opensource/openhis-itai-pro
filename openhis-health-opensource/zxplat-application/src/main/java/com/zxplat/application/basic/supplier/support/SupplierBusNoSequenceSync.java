/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.supplier.support;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商/生产厂家注册前对齐 {@code SP} 业务序号：避免库内已有 bus_no 大于 zxplat_sequence 时撞号。
 *
 * <p>独立事务提交，即使后续写段回滚，序号抬升仍保留（下一趟采番从已占用后缀之后继续）。</p>
 * <p>典型场景：历史迁入 {@code cus_supplier} 后手工/导入自动建厂家，连续 10 次采番均碰撞并抛「生成供应商编号失败」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierBusNoSequenceSync {

    private final JdbcTemplate jdbcTemplate;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 将当前租户 {@code SP} 序号抬到 {@code cus_supplier} 已有最大后缀。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncFromExistingSuppliers() {
        Long maxSuffix =
            jdbcTemplate.query(
                "SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) "
                    + "FROM cus_supplier "
                    + "WHERE NOT is_deleted AND bus_no ~ '^SP[0-9]+$'",
                rs -> {
                    // 无行或聚合为空：无需抬升序号
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getObject(1, Long.class);
                });
        // 库内尚无标准 SP 编号，保持现有序列不动
        if (maxSuffix == null || maxSuffix <= 0L) {
            return;
        }
        long after = businessSequenceService.ensureAtLeast(
            AggregateTypeConstants.SUPPLIER, maxSuffix);
        log.info("供应商注册对齐 SP 序号：库内最大后缀={}, 对齐后 current_value={}", maxSuffix, after);
    }
}
