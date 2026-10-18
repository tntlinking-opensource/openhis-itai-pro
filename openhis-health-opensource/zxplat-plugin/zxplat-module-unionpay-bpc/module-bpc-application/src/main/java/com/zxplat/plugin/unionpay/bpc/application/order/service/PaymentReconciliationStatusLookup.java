/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.application.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.BadSqlGrammarException;
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
     * 支付草稿是否仍有未完成的医保预结算（门诊/住院 2206 状态 ACTIVE）。
     *
     * <p>银联预下单前用来拦住门诊「先扣银联、后 2207」：2207 失败时钱已扣、就诊停在待挂号。
     * 2207 成功后门诊预结算离开 ACTIVE，扫码放行。住院结算仍是先扫码再 2304，不在此拦截。
     * 未部署医保表时视为无预结算。</p>
     *
     * @param tenantId  租户；空则不按租户过滤
     * @param paymentId 支付草稿主键
     * @return 存在门诊 ACTIVE 预结算时为 true
     */
    public boolean hasActiveInsurancePreSettle(Long tenantId, Long paymentId) {
        if (paymentId == null) {
            return false;
        }
        return this.countActivePreSettle("yb_clinic_pre_settle", tenantId, paymentId) > 0L;
    }

    /**
     * 统计指定预结算表中、挂在该支付草稿上的 ACTIVE 行。
     *
     * @param tableName 预结算表（门诊或住院）
     * @param tenantId  租户；空则不按租户过滤
     * @param paymentId 支付草稿主键
     * @return 行数；表不存在时为 0
     */
    private long countActivePreSettle(String tableName, Long tenantId, Long paymentId) {
        String sql;
        Object[] params;
        if (tenantId == null) {
            sql = """
                SELECT COUNT(1)
                FROM %s
                WHERE is_deleted = false
                  AND status = 'ACTIVE'
                  AND payment_draft_id = ?
                """.formatted(tableName);
            params = new Object[] {paymentId};
        } else {
            sql = """
                SELECT COUNT(1)
                FROM %s
                WHERE tenant_id = ?
                  AND is_deleted = false
                  AND status = 'ACTIVE'
                  AND payment_draft_id = ?
                """.formatted(tableName);
            params = new Object[] {tenantId, paymentId};
        }
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, params);
            if (count == null) {
                return 0L;
            }
            return count.longValue();
        } catch (BadSqlGrammarException ex) {
            Throwable cause = ex.getMostSpecificCause();
            String message = "";
            if (cause != null && cause.getMessage() != null) {
                message = cause.getMessage();
            }
            // 未装医保模块时表不存在，不拦截纯自费扫码；其它 SQL 错误继续抛出
            boolean tableMissing = message.contains("does not exist") || message.contains("不存在");
            if (tableMissing) {
                return 0L;
            }
            throw ex;
        }
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
