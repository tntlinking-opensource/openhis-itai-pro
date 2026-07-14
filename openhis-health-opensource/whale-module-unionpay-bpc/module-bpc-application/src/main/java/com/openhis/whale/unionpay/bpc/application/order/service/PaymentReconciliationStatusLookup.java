/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.application.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 读取 fin_payment_reconciliation.status_enum（unionpay-bpc 不依赖 health-domain）。 */
@Component
@RequiredArgsConstructor
public class PaymentReconciliationStatusLookup {

    private final JdbcTemplate jdbcTemplate;

    public Map<Long, String> loadStatuses(Long tenantId, Collection<Long> paymentIds) {
        if (tenantId == null || paymentIds == null || paymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = paymentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String inClause = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                SELECT id, status_enum
                FROM fin_payment_reconciliation
                WHERE tenant_id = ? AND is_deleted = false AND id IN (%s)
                """.formatted(inClause);

        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) {
            params[i + 1] = ids.get(i);
        }

        Map<Long, String> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getLong("id"), rs.getString("status_enum"));
        }, params);
        return result;
    }
}
