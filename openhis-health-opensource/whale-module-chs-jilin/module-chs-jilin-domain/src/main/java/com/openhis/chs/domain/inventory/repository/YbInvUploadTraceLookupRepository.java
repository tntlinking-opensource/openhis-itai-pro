/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 进销存追溯码复杂只读查询实现。
 */
@Repository
@RequiredArgsConstructor
public class YbInvUploadTraceLookupRepository implements YbInvUploadTraceLookup {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询成功上报的追溯码行，用于本地与平台差异比对。
     *
     * @param tenantId  租户主键
     * @param infnoList 限定 infno 列表
     * @param pageable  分页上限
     * @return 追溯码 + 上报主流水字段
     */
    @Override
    public List<InvUploadTraceCompareRow> findTraceRowsForCompare(
        Long tenantId,
        List<String> infnoList,
        Pageable pageable
    ) {
        if (infnoList == null || infnoList.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
            SELECT t.id AS trace_id,
                   t.drug_trac_codg,
                   u.id AS upload_id,
                   u.contract_bus_no,
                   u.infno,
                   u.fixmedins_bchno,
                   u.med_list_codg,
                   u.upload_status,
                   u.created_at
            FROM yb_inv_upload_trace t
            INNER JOIN yb_inv_upload u ON u.id = t.upload_id
            WHERE u.tenant_id = ?
              AND u.upload_status = 'SUCCESS'
              AND u.infno IN (
            """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        for (int i = 0; i < infnoList.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(infnoList.get(i));
        }
        sql.append("""
            )
            ORDER BY t.id DESC
            LIMIT ? OFFSET ?
            """);
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            InvUploadTraceCompareRow row = new InvUploadTraceCompareRow();
            row.setTraceId(rs.getLong("trace_id"));
            row.setDrugTracCodg(rs.getString("drug_trac_codg"));
            row.setUploadId(rs.getLong("upload_id"));
            row.setContractBusNo(rs.getString("contract_bus_no"));
            row.setInfno(rs.getString("infno"));
            row.setFixmedinsBchno(rs.getString("fixmedins_bchno"));
            row.setMedListCodg(rs.getString("med_list_codg"));
            row.setUploadStatus(rs.getString("upload_status"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                row.setCreatedAt(createdAt.toLocalDateTime());
            }
            return row;
        }, args.toArray());
    }
}
