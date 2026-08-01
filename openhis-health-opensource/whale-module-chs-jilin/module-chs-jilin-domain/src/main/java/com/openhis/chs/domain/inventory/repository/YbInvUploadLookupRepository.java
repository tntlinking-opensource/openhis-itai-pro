/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.repository.support.YbInvUploadRowMapper;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
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

    /**
     * 运维本地流水多条件 AND 筛选。
     *
     * @param tenantId       租户主键
     * @param contractBusNo  契约业务编号；空则不限
     * @param infno          交易号；空则不限
     * @param sourceType     来源类型；空则不限
     * @param sourceId       来源主键；空则不限
     * @param uploadStatus   上报状态；空则不限；SKIPPED 匹配 SKIPPED%
     * @param fixmedinsBchno 批次流水号；空则不限；模糊匹配
     * @param createdFrom    创建时间起（含）；空则不限
     * @param createdTo      创建时间止（含）；空则不限
     * @param pageable       分页
     * @return 按 id 降序的流水列表
     */
    @Override
    public List<YbInvUpload> findLocalRecords(
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
    ) {
        // Gate：无租户则无法隔离流水
        if (tenantId == null) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
            SELECT *
            FROM yb_inv_upload
            WHERE tenant_id = ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        // Pre：各筛选项空则跳过，非空则 AND
        if (isNotEmpty(contractBusNo)) {
            sql.append(" AND contract_bus_no = ?");
            args.add(contractBusNo.trim());
        }
        if (isNotEmpty(infno)) {
            sql.append(" AND infno = ?");
            args.add(infno.trim());
        }
        if (isNotEmpty(sourceType)) {
            sql.append(" AND source_type = ?");
            args.add(sourceType.trim());
        }
        if (sourceId != null) {
            sql.append(" AND source_id = ?");
            args.add(sourceId);
        }
        if (isNotEmpty(uploadStatus)) {
            String status = uploadStatus.trim();
            // SKIPPED 覆盖 SKIPPED_NO_YB_NO 等扩展码
            if (ChsInventoryConstants.UPLOAD_STATUS_SKIPPED.equals(status)) {
                sql.append(" AND upload_status LIKE ?");
                args.add(ChsInventoryConstants.UPLOAD_STATUS_SKIPPED + "%");
            } else {
                sql.append(" AND upload_status = ?");
                args.add(status);
            }
        }
        if (isNotEmpty(fixmedinsBchno)) {
            // 批次号支持模糊，避免必须整串精确输入
            sql.append(" AND fixmedins_bchno LIKE ?");
            args.add("%" + fixmedinsBchno.trim() + "%");
        }
        if (createdFrom != null) {
            sql.append(" AND created_at >= ?");
            args.add(createdFrom);
        }
        if (createdTo != null) {
            sql.append(" AND created_at <= ?");
            args.add(createdTo);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        int pageSize = 100;
        long offset = 0L;
        if (pageable != null) {
            pageSize = pageable.getPageSize();
            offset = pageable.getOffset();
        }
        args.add(pageSize);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), YbInvUploadRowMapper.INSTANCE, args.toArray());
    }
}
