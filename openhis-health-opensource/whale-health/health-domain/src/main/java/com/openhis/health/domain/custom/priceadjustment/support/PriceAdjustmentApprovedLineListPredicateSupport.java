/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.support;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentApprovedLineListQuery;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
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
     * 组装软删、已生效、生效时刻窗、项目表、项目主键 IN、单号谓词。
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
        return predicates;
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
