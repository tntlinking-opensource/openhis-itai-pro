/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.repository;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.inventory.repository.support.YbInvUploadRowMapper;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
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
            // 无可重试状态集合：直接空列表，避免拼出非法 IN ()
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
            """);
        args.add(maxRetry);
        args.add(now);
        // 仅排除「191 + 主档无码」历史挂账；主档有码的 191 仍可进重试队列
        appendExcludeHistoricalInboundTraceMissing(sql, args);
        sql.append("""
            ORDER BY next_retry_at ASC NULLS FIRST, id ASC
            LIMIT ? OFFSET ?
            """);
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
        // 补传列表：仅隐藏「191 + 主档无码」；主档有码须可见（BR-CHS-INV-011）
        appendExcludeHistoricalInboundTraceMissing(sql, args);
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

    /**
     * 报文中是否至少有一条追溯码存在于本租户 {@code cus_trace_number} 主档。
     *
     * @param tenantId    租户主键
     * @param requestJson 持久化 request_json；可空
     * @return 任一码在主档为 true
     */
    @Override
    public boolean hasAnyRequestDrugTracInMaster(Long tenantId, String requestJson) {
        // Gate：无租户或无报文则无法判定主档命中
        if (tenantId == null || isEmpty(requestJson)) {
            return false;
        }
        Boolean hit = jdbcTemplate.query(
            """
                SELECT 1
                WHERE EXISTS (
                  SELECT 1
                  FROM regexp_matches(?, '"drug_trac_codg"[[:space:]]*:[[:space:]]*"([^"]+)"', 'g') AS rm(caps)
                  INNER JOIN cus_trace_number t
                    ON t.is_deleted = false
                   AND t.tenant_id = ?
                   AND t.trace_no = rm.caps[1]
                )
                LIMIT 1
                """,
            rs -> {
                // 任一码在 cus_trace_number 即视为院内入过库
                if (rs.next()) {
                    return Boolean.TRUE;
                }
                return Boolean.FALSE;
            },
            requestJson,
            tenantId
        );
        return Boolean.TRUE.equals(hit);
    }

    /**
     * 追加「排除历史 191」条件：平台 191 文案且报文追溯码均不在 {@code cus_trace_number}。
     *
     * <p>主档有任一码 → 不排除（院内入过库，运维须处理）。</p>
     *
     * @param sql  正在拼装的 SQL（WHERE 已开始）
     * @param args 绑定参数列表（本方法追加两个 LIKE 模式）
     */
    private void appendExcludeHistoricalInboundTraceMissing(StringBuilder sql, List<Object> args) {
        sql.append("""
             AND NOT (
               platform_msg IS NOT NULL
               AND (
                 platform_msg LIKE ?
                 OR platform_msg LIKE ?
               )
               AND NOT EXISTS (
                 SELECT 1
                 FROM regexp_matches(COALESCE(request_json, ''), '"drug_trac_codg"[[:space:]]*:[[:space:]]*"([^"]+)"', 'g') AS rm(caps)
                 INNER JOIN cus_trace_number t
                   ON t.is_deleted = false
                  AND t.tenant_id = yb_inv_upload.tenant_id
                  AND t.trace_no = rm.caps[1]
               )
             )
            """);
        args.add("%" + ChsInventoryConstants.PLATFORM_MSG_INBOUND_TRACE_MISSING_CODE + "%");
        args.add("%" + ChsInventoryConstants.PLATFORM_MSG_INBOUND_TRACE_MISSING_TEXT + "%");
    }

    /**
     * 按追溯码查找最近一次入库类成功 3502 的契约 bus_no（BR-CHS-INV-015）。
     *
     * @param tenantId      租户主键
     * @param drugTracCodgs 追溯码列表
     * @return bus_no；无命中 null
     */
    @Override
    public String findLatestInboundStockContractBusNo(Long tenantId, List<String> drugTracCodgs) {
        // Pre：无租户或无码 → 无法定位入库平台
        if (tenantId == null || drugTracCodgs == null || drugTracCodgs.isEmpty()) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        for (String raw : drugTracCodgs) {
            if (isEmpty(raw)) {
                continue;
            }
            String trimmed = raw.trim();
            if (isEmpty(trimmed)) {
                continue;
            }
            codes.add(trimmed);
        }
        // Pre：全空码 → 与无码同处理
        if (codes.isEmpty()) {
            return null;
        }
        StringBuilder sql = new StringBuilder("""
            SELECT u.contract_bus_no
            FROM yb_inv_upload_trace t
            INNER JOIN yb_inv_upload u
              ON u.id = t.upload_id
             AND u.tenant_id = t.tenant_id
             AND u.is_deleted = false
            WHERE t.tenant_id = ?
              AND t.drug_trac_codg IN (
            """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(codes.get(i));
        }
        // Gate：仅入库类变更（正库存）且平台 SUCCESS；含软删 trace 以保留「再挂码」前的入库归属
        sql.append("""
            )
              AND u.infno = ?
              AND u.upload_status = ?
              AND COALESCE(u.request_json::json->'invinfo'->>'inv_chg_type', '') IN ('101','103','106','108','110')
            ORDER BY u.created_at DESC NULLS LAST, u.id DESC
            LIMIT 1
            """);
        args.add(ChsInfNo.INVENTORY_CHANGE);
        args.add(ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        List<String> rows = jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> rs.getString(1),
            args.toArray());
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String busNo = rows.get(0);
        if (isEmpty(busNo)) {
            return null;
        }
        return busNo.trim();
    }
}
