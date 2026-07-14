/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 进销存追溯码复杂只读查询（JdbcTemplate；Repository 接口禁 {@code @Query}） */
public interface YbInvUploadTraceLookup {

    /**
     * 查询成功上报的追溯码行，用于本地与平台差异比对。
     *
     * @param tenantId 租户
     * @param infnoList 限定 infno 列表
     * @param pageable 分页上限
     * @return 追溯码 + 上报主流水字段 */
    List<InvUploadTraceCompareRow> findTraceRowsForCompare(
        Long tenantId,
        List<String> infnoList,
        Pageable pageable
    );
}
