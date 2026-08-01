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

    /**
     * 原支付是否已有完成态逆向退费单（{@code relation_id = 原 paymentId} 且 {@code REFUNDED}）。
     *
     * <p>用于识别「HIS 已退、银联未退」孤儿单（门诊退费/退号原路逆向）。</p>
     */
    public boolean hasCompletedReverseRefund(Long tenantId, Long originalPaymentId) {
        if (tenantId == null || originalPaymentId == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(1)
                FROM fin_payment_reconciliation
                WHERE tenant_id = ?
                  AND is_deleted = false
                  AND relation_id = ?
                  AND status_enum = 'REFUNDED'
                """,
            Long.class,
            tenantId,
            originalPaymentId);
        return count != null && count > 0;
    }

    /**
     * 批量：原支付 id → 是否已有完成态逆向退费单。
     */
    public Map<Long, Boolean> loadHasCompletedReverseRefund(Long tenantId, Collection<Long> originalPaymentIds) {
        if (tenantId == null || originalPaymentIds == null || originalPaymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = originalPaymentIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String inClause = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
            SELECT DISTINCT relation_id
            FROM fin_payment_reconciliation
            WHERE tenant_id = ?
              AND is_deleted = false
              AND status_enum = 'REFUNDED'
              AND relation_id IN (%s)
            """.formatted(inClause);

        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) {
            params[i + 1] = ids.get(i);
        }

        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : ids) {
            result.put(id, false);
        }
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getLong("relation_id"), true);
        }, params);
        return result;
    }
}
