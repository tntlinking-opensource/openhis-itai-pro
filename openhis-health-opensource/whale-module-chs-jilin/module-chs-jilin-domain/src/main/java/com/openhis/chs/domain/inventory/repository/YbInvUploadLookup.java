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
}
