/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.support;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 耗材目录导入/新建前对齐 {@code DD} 业务序号：避免库内已有 bus_no 大于 zxplat_sequence 时撞号。
 *
 * <p>独立事务提交，即使后续导入写段回滚，序号抬升仍保留（下一趟采番从已占用后缀之后继续）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCatalogBusNoSequenceSync {

    private final JdbcTemplate jdbcTemplate;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 将当前租户 {@code DD} 序号抬到 {@code adm_device_definition} 已有最大后缀。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncFromExistingDefinitions() {
        Long maxSuffix =
            jdbcTemplate.query(
                "SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) "
                    + "FROM adm_device_definition "
                    + "WHERE NOT is_deleted AND bus_no ~ '^DD[0-9]+$'",
                rs -> rs.next() ? rs.getObject(1, Long.class) : null);
        if (maxSuffix == null || maxSuffix <= 0L) {
            return;
        }
        long after = businessSequenceService.ensureAtLeast(
            AggregateTypeConstants.DEVICE_DEFINITION, maxSuffix);
        log.info("耗材目录导入对齐 DD 序号：库内最大后缀={}, 对齐后 current_value={}", maxSuffix, after);
    }
}
