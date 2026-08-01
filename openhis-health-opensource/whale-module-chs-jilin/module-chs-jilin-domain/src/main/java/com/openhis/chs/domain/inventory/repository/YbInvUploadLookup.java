/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 进销存上报流水复杂只读查询（JdbcTemplate；Repository 接口禁 {@code @Query}） */
public interface YbInvUploadLookup {

    /**
     * 查询可重试的上报流水：失败/待上报且未超最大重试次数、已到重试时间。
     *
     * @param tenantId 租户
     * @param statuses 可重试状态列表
     * @param maxRetry 最大重试次数（不含）
     * @param now 当前时间
     * @param pageable 批次分页
     * @return 待重试流水，按 next_retry_at、id 升序 */
    List<YbInvUpload> findRetryable(
        Long tenantId,
        List<String> statuses,
        int maxRetry,
        LocalDateTime now,
        Pageable pageable
    );

    /**
     * 运维本地流水多条件 AND 筛选（契约/交易号/来源/状态/批次号/创建时间均可空）。
     *
     * @param tenantId       租户主键
     * @param contractBusNo  契约业务编号；空则不限
     * @param infno          交易号；空则不限
     * @param sourceType     来源类型；空则不限
     * @param sourceId       来源主键；空则不限
     * @param uploadStatus   上报状态；空则不限；SKIPPED 前缀匹配
     * @param fixmedinsBchno 批次流水号；空则不限；模糊 LIKE
     * @param createdFrom    创建时间起（含）；空则不限
     * @param createdTo      创建时间止（含）；空则不限
     * @param pageable       分页（limit/offset）
     * @return 按 id 降序的流水列表
     */
    List<YbInvUpload> findLocalRecords(
        Long tenantId,
        String contractBusNo,
        String infno,
        String sourceType,
        Long sourceId,
        String uploadStatus,
        String fixmedinsBchno,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        Pageable pageable
    );
}
