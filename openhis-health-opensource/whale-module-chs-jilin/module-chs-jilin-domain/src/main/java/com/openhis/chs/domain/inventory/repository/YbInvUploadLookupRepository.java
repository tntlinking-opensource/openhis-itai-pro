/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.repository.support.YbInvUploadRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 进销存上报流水复杂只读查询实现。
 */
@Repository
@RequiredArgsConstructor
public class YbInvUploadLookupRepository implements YbInvUploadLookup {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询可重试的上报流水：失败/待上报且未超最大重试次数、已到重试时间。
     *
     * @param tenantId 租户主键
     * @param statuses 可重试状态列表
     * @param maxRetry 最大重试次数（不含）
     * @param now      当前时间
     * @param pageable 批次分页
     * @return 待重试流水，按 next_retry_at、id 升序
     */
    @Override
    public List<YbInvUpload> findRetryable(
        Long tenantId,
        List<String> statuses,
        int maxRetry,
        LocalDateTime now,
        Pageable pageable
    ) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
            SELECT *
            FROM yb_inv_upload
            WHERE tenant_id = ?
              AND upload_status IN (
            """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(statuses.get(i));
        }
        sql.append("""
            )
              AND retry_count < ?
              AND (next_retry_at IS NULL OR next_retry_at <= ?)
              AND request_json IS NOT NULL
            ORDER BY next_retry_at ASC NULLS FIRST, id ASC
            LIMIT ? OFFSET ?
            """);
        args.add(maxRetry);
        args.add(now);
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        return jdbcTemplate.query(sql.toString(), YbInvUploadRowMapper.INSTANCE, args.toArray());
    }
}
