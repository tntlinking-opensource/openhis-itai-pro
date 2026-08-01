/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.catalog.result.HospitalCatalogPresence;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * JDBC 实现：按 list_type 查对应定义表 bus_no（含停用，排除软删）。
 */
@Repository
@RequiredArgsConstructor
public class HospitalCatalogPresenceLookupRepository implements HospitalCatalogPresenceLookup {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, HospitalCatalogPresence> resolve(String listType, Collection<String> busNos) {
        Map<String, HospitalCatalogPresence> out = new HashMap<>();
        if (busNos == null || busNos.isEmpty()) {
            return out;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String busNo : busNos) {
            if (!isEmpty(busNo)) {
                codes.add(busNo.trim());
            }
        }
        if (codes.isEmpty()) {
            return out;
        }
        String table = HospitalCatalogPresenceLookup.productTableOf(listType);
        String lt = listType == null ? "" : listType.trim();
        String placeholders = String.join(",", codes.stream().map(c -> "?").toList());
        Long tenantId = TenantUtils.getCurrentTenantId();
        String sql = "SELECT id, bus_no, status_enum, yb_no FROM " + table
            + " WHERE is_deleted = false AND tenant_id = ? AND bus_no IN (" + placeholders + ")";
        Object[] args = new Object[1 + codes.size()];
        args[0] = tenantId;
        int i = 1;
        for (String code : codes) {
            args[i++] = code;
        }
        jdbcTemplate.query(sql, rs -> {
            String busNo = rs.getString("bus_no");
            out.put(
                HospitalCatalogPresenceLookup.key(lt, busNo),
                new HospitalCatalogPresence()
                    .setProductTable(table)
                    .setProductId(rs.getLong("id"))
                    .setStatusEnum(rs.getString("status_enum"))
                    .setYbNo(rs.getString("yb_no")));
        }, args);
        return out;
    }
}
