/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.support;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentApprovedLineListQuery;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 已生效调价项目主行列表动态 WHERE（{@code cus_price_adjustment}，按主行分页）。
 */
public final class PriceAdjustmentApprovedLineListPredicateSupport {

    private PriceAdjustmentApprovedLineListPredicateSupport() {
    }

    /**
     * 组装软删、已生效、生效时刻窗、项目表、项目主键 IN、单号、排除调价原因谓词。
     *
     * @param root  调价主行根
     * @param cb    条件构建器
     * @param query 过滤入参；仅软删时 query 可为 null
     * @return 谓词列表
     */
    public static List<Predicate> collectPredicates(
        Root<PriceAdjustment> root,
        CriteriaBuilder cb,
        PriceAdjustmentApprovedLineListQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.equal(
            root.get(FieldNameConstants.STATUS_ENUM),
            PriceAdjustmentStatus.APPROVED.getCode()
        ));
        if (query == null) {
            return predicates;
        }
        if (StringUtils.isNotEmpty(query.getItemTable())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
        }
        if (query.getItemIds() != null) {
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(query.getItemIds()));
        }
        Date start = toDate(query.getStartDate());
        Date end = toDate(query.getEndDate());
        if (start != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.APPROVE_TIME), start));
        }
        if (end != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.APPROVE_TIME), end));
        }
        if (StringUtils.isNotEmpty(query.getBusNo())) {
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getBusNo().trim());
            predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
        }
        // 排除指定调价原因（院点自动调价审计等）：谓词层剔除，count/分页一致
        List<String> excludeReasons = normalizeExcludeAdjustmentReasons(query.getExcludeAdjustmentReasons());
        if (!excludeReasons.isEmpty()) {
            predicates.add(cb.not(root.get(FieldNameConstants.ADJUSTMENT_REASON).in(excludeReasons)));
        }
        return predicates;
    }

    /**
     * 规范化排除原因列表：去空、trim；入参 null 或全空则返回空列表。
     *
     * @param raw 原始排除原因
     * @return 非 null 列表（可空）
     */
    private static List<String> normalizeExcludeAdjustmentReasons(List<String> raw) {
        List<String> normalized = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return normalized;
        }
        for (String reason : raw) {
            if (StringUtils.isEmpty(reason)) {
                continue;
            }
            normalized.add(reason.trim());
        }
        return normalized;
    }

    /**
     * 已生效调价行：单号模糊时 SQL 层「精确全等在前」（与 LIKE 列 busNo 同集合）。
     *
     * @param cq              CriteriaQuery；null 或 count 时工具内部跳过
     * @param root            调价主行根
     * @param cb              CriteriaBuilder
     * @param query           列表查询；无 busNo 时不写排序
     * @param secondaryOrders 次级排序（如生效时间/主键倒序）；不可为 null
     */
    public static void applyBusNoExactFirstOrder(
        CriteriaQuery<?> cq,
        Root<PriceAdjustment> root,
        CriteriaBuilder cb,
        PriceAdjustmentApprovedLineListQuery query,
        List<Order> secondaryOrders) {
        if (query == null || StringUtils.isEmpty(query.getBusNo())) {
            return;
        }
        String trimmed = query.getBusNo().trim();
        KeywordExactFirstOrderSupport.applyExactFirstOrder(
            cq,
            cb,
            trimmed,
            secondaryOrders,
            root.get(FieldNameConstants.BUS_NO));
    }

    /**
     * LocalDateTime → java.util.Date（系统默认时区）；null 输入 null。
     *
     * @param value 本地时间
     * @return Date 或 null
     */
    private static Date toDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
