/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.platformdict.repository;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;
import com.zxplat.plugin.chs.domain.platformdict.command.UpsertPlatformDictRowCommand;
import com.zxplat.framework.core.infrastructure.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** 1901 字典 PostgreSQL 批量 upsert */
@Repository
@RequiredArgsConstructor
public class YbPlatformDictBulkUpsertRepository {

    private static final String UPSERT = """
        INSERT INTO yb_platform_dict_item (
            id, tenant_id, dict_type, dict_value, dict_label,
            parent_value, sort_order, vali_flag, platform_version,
            query_admdvs, source_contract_id, synced_at,
            created_at, updated_at, created_by, updated_by, is_deleted
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,false)
        ON CONFLICT (tenant_id, dict_type, dict_value)
            WHERE is_deleted = false
        DO UPDATE SET
            dict_label = EXCLUDED.dict_label,
            parent_value = EXCLUDED.parent_value,
            sort_order = EXCLUDED.sort_order,
            vali_flag = EXCLUDED.vali_flag,
            platform_version = EXCLUDED.platform_version,
            query_admdvs = EXCLUDED.query_admdvs,
            source_contract_id = EXCLUDED.source_contract_id,
            synced_at = EXCLUDED.synced_at,
            updated_at = EXCLUDED.updated_at,
            updated_by = EXCLUDED.updated_by
        """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * PostgreSQL {@code ON CONFLICT} 批量 upsert 1901 字典行。
     *
     * @param tenantId         租户
     * @param operatorId       操作人
     * @param sourceContractId 来源契约
     * @param queryAdmdvs      查询区划
     * @param syncedAt         同步时间戳
     * @param rows             待写入行
     * @return JDBC batch 影响行数合计
     */
    public int bulkUpsert(
            Long tenantId,
            Long operatorId,
            Long sourceContractId,
            String queryAdmdvs,
            LocalDateTime syncedAt,
            List<UpsertPlatformDictRowCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        final Timestamp now;
        if (syncedAt != null) {
            now = Timestamp.valueOf(syncedAt);
        } else {
            now = Timestamp.valueOf(LocalDateTime.now());
        }
        int[][] counts = jdbcTemplate.batchUpdate(UPSERT, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, SnowflakeIdGenerator.nextId());
            ps.setLong(2, tenantId);
            ps.setString(3, row.getDictType());
            ps.setString(4, row.getDictValue());
            ps.setString(5, row.getDictLabel());
            ps.setString(6, trimToNull(row.getParentValue()));
            if (row.getSortOrder() != null) {
                ps.setInt(7, row.getSortOrder());
            } else {
                ps.setObject(7, null);
            }
            ps.setString(8, row.getValiFlag());
            if (row.getPlatformVersion() != null) {
                ps.setLong(9, row.getPlatformVersion());
            } else {
                ps.setObject(9, null);
            }
            ps.setString(10, queryAdmdvs);
            if (sourceContractId != null) {
                ps.setLong(11, sourceContractId);
            } else {
                ps.setObject(11, null);
            }
            ps.setTimestamp(12, now);
            ps.setTimestamp(13, now);
            ps.setTimestamp(14, now);
            if (operatorId != null) {
                ps.setLong(15, operatorId);
                ps.setLong(16, operatorId);
            } else {
                ps.setObject(15, null);
                ps.setObject(16, null);
            }
        });
        int total = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                total += c;
            }
        }
        return total;
    }
}
