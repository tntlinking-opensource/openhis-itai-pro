/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.application.order.service;

import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.unionpay.bpc.domain.query.command.PageBpcOrdersCommand;
import com.zxplat.plugin.unionpay.bpc.domain.query.result.PageBpcOrdersResult;
import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrder;
import com.zxplat.plugin.unionpay.bpc.domain.order.repository.BpcOrderRepository;
import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrderStatus;
import com.zxplat.plugin.unionpay.bpc.domain.share.BpcTranType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 财务银联订单查询。 */
@Service
@RequiredArgsConstructor
public class UnionPayBpcOrderQueryService {

    private static final String PAYMENT_DRAFT = "DRAFT";

    private final BpcOrderRepository bpcOrderRepository;

    private final PaymentReconciliationStatusLookup paymentStatusLookup;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public PageBpcOrdersResult pageOrders(PageBpcOrdersCommand command) {
        PageBpcOrdersCommand safe = command == null ? new PageBpcOrdersCommand() : command;
        int pageNo = safe.getPageNo() == null || safe.getPageNo() < 1 ? 1 : safe.getPageNo();
        int pageSize = safe.getPageSize() == null || safe.getPageSize() < 1 ? 20 : Math.min(safe.getPageSize(), 200);

        Long tenantId = TenantUtils.getCurrentTenantId();
        if (Boolean.TRUE.equals(safe.getPendingSettleOnly())) {
            return pagePendingSettleOrders(safe, tenantId, pageNo, pageSize);
        }
        if (Boolean.TRUE.equals(safe.getOrphanRefundOnly())) {
            return pageOrphanRefundOrders(safe, tenantId, pageNo, pageSize);
        }

        Specification<BpcOrder> spec = buildSpec(tenantId, safe);
        Page<BpcOrder> page = bpcOrderRepository.findAll(
                spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        PageBpcOrdersResult result = new PageBpcOrdersResult().setTotal(page.getTotalElements());
        List<PageBpcOrdersResult.BpcOrderListItemDto> rows = new ArrayList<>();
        for (BpcOrder order : page.getContent()) {
            rows.add(toListItem(order));
        }
        enrichPaymentStatus(tenantId, rows);
        result.setRows(rows);
        return result;
    }

    private PageBpcOrdersResult pagePendingSettleOrders(
            PageBpcOrdersCommand filter,
            Long tenantId,
            int pageNo,
            int pageSize) {
        PendingSettleSql sql = buildPendingSettleSql(filter, tenantId);
        Long total = jdbcTemplate.queryForObject(sql.countSql, Long.class, sql.params.toArray());
        long totalCount = total == null ? 0L : total;

        List<Object> pageParams = new ArrayList<>(sql.params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);

        List<Long> orderIds = jdbcTemplate.query(
                sql.pageSql,
                (rs, rowNum) -> rs.getLong("id"),
                pageParams.toArray());

        PageBpcOrdersResult result = new PageBpcOrdersResult().setTotal(totalCount);
        if (orderIds.isEmpty()) {
            result.setRows(List.of());
            return result;
        }

        Map<Long, BpcOrder> orderMap = bpcOrderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(BpcOrder::getId, o -> o, (a, b) -> a, LinkedHashMap::new));
        List<PageBpcOrdersResult.BpcOrderListItemDto> rows = new ArrayList<>();
        for (Long orderId : orderIds) {
            BpcOrder order = orderMap.get(orderId);
            if (order != null) {
                rows.add(toListItem(order));
            }
        }
        enrichPaymentStatus(tenantId, rows);
        result.setRows(rows);
        return result;
    }

    private PageBpcOrdersResult pageOrphanRefundOrders(
            PageBpcOrdersCommand filter,
            Long tenantId,
            int pageNo,
            int pageSize) {
        PendingSettleSql sql = buildOrphanRefundSql(filter, tenantId);
        Long total = jdbcTemplate.queryForObject(sql.countSql, Long.class, sql.params.toArray());
        long totalCount = total == null ? 0L : total;

        List<Object> pageParams = new ArrayList<>(sql.params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);

        List<Long> orderIds = jdbcTemplate.query(
                sql.pageSql,
                (rs, rowNum) -> rs.getLong("id"),
                pageParams.toArray());

        PageBpcOrdersResult result = new PageBpcOrdersResult().setTotal(totalCount);
        if (orderIds.isEmpty()) {
            result.setRows(List.of());
            return result;
        }

        Map<Long, BpcOrder> orderMap = bpcOrderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(BpcOrder::getId, o -> o, (a, b) -> a, LinkedHashMap::new));
        List<PageBpcOrdersResult.BpcOrderListItemDto> rows = new ArrayList<>();
        for (Long orderId : orderIds) {
            BpcOrder order = orderMap.get(orderId);
            if (order != null) {
                rows.add(toListItem(order));
            }
        }
        enrichPaymentStatus(tenantId, rows);
        result.setRows(rows);
        return result;
    }

    private PendingSettleSql buildPendingSettleSql(PageBpcOrdersCommand filter, Long tenantId) {
        StringBuilder where = new StringBuilder("""
                FROM upc_bpc_order o
                INNER JOIN fin_payment_reconciliation p
                  ON p.id = o.payment_id AND p.tenant_id = o.tenant_id
                WHERE o.tenant_id = ?
                  AND o.is_deleted = false
                  AND p.is_deleted = false
                  AND o.status = ?
                  AND o.tran_type = ?
                  AND p.status_enum = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(BpcOrderStatus.SUCCESS.getCode());
        params.add(BpcTranType.CONSUMER_SCAN.getCode());
        params.add(PAYMENT_DRAFT);

        LocalDateTime start = parseStartOfDay(filter.getDateFrom());
        if (start != null) {
            where.append(" AND o.created_at >= ?");
            params.add(start);
        }
        LocalDateTime endExclusive = parseEndExclusive(filter.getDateTo());
        if (endExclusive != null) {
            where.append(" AND o.created_at < ?");
            params.add(endExclusive);
        }
        if (filter.getPaymentId() != null) {
            where.append(" AND o.payment_id = ?");
            params.add(filter.getPaymentId());
        }
        if (StringUtils.hasText(filter.getMerTradeNo())) {
            where.append(" AND o.mer_trade_no LIKE ?");
            params.add("%" + filter.getMerTradeNo().trim() + "%");
        }
        if (StringUtils.hasText(filter.getTradeNo())) {
            where.append(" AND o.trade_no LIKE ?");
            params.add("%" + filter.getTradeNo().trim() + "%");
        }
        if (StringUtils.hasText(filter.getPayType())) {
            where.append(" AND o.pay_type = ?");
            params.add(filter.getPayType().trim());
        }

        String countSql = "SELECT COUNT(1) " + where;
        String pageSql = "SELECT o.id " + where + " ORDER BY o.created_at DESC LIMIT ? OFFSET ?";
        return new PendingSettleSql(countSql, pageSql, params);
    }

    private PendingSettleSql buildOrphanRefundSql(PageBpcOrdersCommand filter, Long tenantId) {
        StringBuilder where = new StringBuilder("""
                FROM upc_bpc_order o
                WHERE o.tenant_id = ?
                  AND o.is_deleted = false
                  AND o.status = ?
                  AND o.tran_type = ?
                  AND EXISTS (
                    SELECT 1
                    FROM fin_payment_reconciliation r
                    WHERE r.tenant_id = o.tenant_id
                      AND r.is_deleted = false
                      AND r.relation_id = o.payment_id
                      AND r.status_enum = 'REFUNDED'
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM upc_bpc_order x
                    WHERE x.tenant_id = o.tenant_id
                      AND x.is_deleted = false
                      AND x.payment_id = o.payment_id
                      AND (x.tran_type = ? OR x.status = ?)
                  )
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(BpcOrderStatus.SUCCESS.getCode());
        params.add(BpcTranType.CONSUMER_SCAN.getCode());
        params.add(BpcTranType.REFUND.getCode());
        params.add(BpcOrderStatus.REFUNDED.getCode());

        LocalDateTime start = parseStartOfDay(filter.getDateFrom());
        if (start != null) {
            where.append(" AND o.created_at >= ?");
            params.add(start);
        }
        LocalDateTime endExclusive = parseEndExclusive(filter.getDateTo());
        if (endExclusive != null) {
            where.append(" AND o.created_at < ?");
            params.add(endExclusive);
        }
        if (filter.getPaymentId() != null) {
            where.append(" AND o.payment_id = ?");
            params.add(filter.getPaymentId());
        }
        if (StringUtils.hasText(filter.getMerTradeNo())) {
            where.append(" AND o.mer_trade_no LIKE ?");
            params.add("%" + filter.getMerTradeNo().trim() + "%");
        }
        if (StringUtils.hasText(filter.getTradeNo())) {
            where.append(" AND o.trade_no LIKE ?");
            params.add("%" + filter.getTradeNo().trim() + "%");
        }
        if (StringUtils.hasText(filter.getPayType())) {
            where.append(" AND o.pay_type = ?");
            params.add(filter.getPayType().trim());
        }

        String countSql = "SELECT COUNT(1) " + where;
        String pageSql = "SELECT o.id " + where + " ORDER BY o.created_at DESC LIMIT ? OFFSET ?";
        return new PendingSettleSql(countSql, pageSql, params);
    }

    private void enrichPaymentStatus(Long tenantId, List<PageBpcOrdersResult.BpcOrderListItemDto> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> paymentIds = rows.stream()
                .map(PageBpcOrdersResult.BpcOrderListItemDto::getPaymentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> statusMap = paymentStatusLookup.loadStatuses(tenantId, paymentIds);
        Map<Long, Boolean> reverseRefundMap =
            paymentStatusLookup.loadHasCompletedReverseRefund(tenantId, paymentIds);
        for (PageBpcOrdersResult.BpcOrderListItemDto row : rows) {
            String paymentStatus = statusMap.get(row.getPaymentId());
            row.setPaymentStatus(paymentStatus);
            row.setPendingSettle(isPendingSettle(row, paymentStatus));
            row.setOrphanRefund(isOrphanRefund(row, reverseRefundMap.get(row.getPaymentId())));
        }
    }

    private static boolean isPendingSettle(
            PageBpcOrdersResult.BpcOrderListItemDto row,
            String paymentStatus) {
        return BpcOrderStatus.SUCCESS.getCode().equals(row.getStatus())
                && BpcTranType.CONSUMER_SCAN.getCode().equals(row.getTranType())
                && PAYMENT_DRAFT.equals(paymentStatus);
    }

    private static boolean isOrphanRefund(
            PageBpcOrdersResult.BpcOrderListItemDto row,
            Boolean hasHisReverseRefund) {
        return BpcOrderStatus.SUCCESS.getCode().equals(row.getStatus())
                && BpcTranType.CONSUMER_SCAN.getCode().equals(row.getTranType())
                && Boolean.TRUE.equals(hasHisReverseRefund);
    }

    private static Specification<BpcOrder> buildSpec(Long tenantId, PageBpcOrdersCommand filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));

            LocalDateTime start = parseStartOfDay(filter.getDateFrom());
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            LocalDateTime endExclusive = parseEndExclusive(filter.getDateTo());
            if (endExclusive != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), endExclusive));
            }
            if (filter.getPaymentId() != null) {
                predicates.add(cb.equal(root.get("paymentId"), filter.getPaymentId()));
            }
            if (StringUtils.hasText(filter.getMerTradeNo())) {
                predicates.add(cb.like(root.get("merTradeNo"), "%" + filter.getMerTradeNo().trim() + "%"));
            }
            if (StringUtils.hasText(filter.getTradeNo())) {
                predicates.add(cb.like(root.get("tradeNo"), "%" + filter.getTradeNo().trim() + "%"));
            }
            if (StringUtils.hasText(filter.getStatus())) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus().trim()));
            }
            if (StringUtils.hasText(filter.getPayType())) {
                predicates.add(cb.equal(root.get("payType"), filter.getPayType().trim()));
            }
            if (StringUtils.hasText(filter.getTranType())) {
                predicates.add(cb.equal(root.get("tranType"), filter.getTranType().trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static LocalDateTime parseStartOfDay(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim()).atStartOfDay();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDateTime parseEndExclusive(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim()).plusDays(1).atStartOfDay();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static PageBpcOrdersResult.BpcOrderListItemDto toListItem(BpcOrder order) {
        return new PageBpcOrdersResult.BpcOrderListItemDto()
                .setOrderId(order.getId())
                .setPaymentId(order.getPaymentId())
                .setDetailIndex(order.getDetailIndex())
                .setMerTradeNo(order.getMerTradeNo())
                .setTradeNo(order.getTradeNo())
                .setTranType(order.getTranType())
                .setTxnAmtFen(order.getTxnAmtFen())
                .setPaidAmtFen(order.getPaidAmtFen())
                .setPayType(order.getPayType())
                .setStatus(order.getStatus())
                .setRespCode(order.getRespCode())
                .setRespMsg(order.getRespMsg())
                .setPosNo(order.getPosNo())
                .setCreatedAt(formatDateTime(order.getCreatedAt()))
                .setUpdatedAt(formatDateTime(order.getUpdatedAt()));
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private record PendingSettleSql(String countSql, String pageSql, List<Object> params) {
    }
}
