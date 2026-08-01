/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.support;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.openhis.health.domain.share.constants.FieldNameConstants;
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
 * 调价单列表动态 WHERE 片段（{@code cus_price_adjustment}，按单号聚合列表复用）。
 */
public final class PriceAdjustmentDocumentListPredicateSupport {

    private PriceAdjustmentDocumentListPredicateSupport() {
    }

    /**
     * 组装软删、机构、状态、列表 coalesce 时刻窗、制单/审批时刻窗、制单人/审批人、品类、关键字与单号模糊谓词。
     *
     * @param root  调价主行根
     * @param cb    条件构建器
     * @param query 过滤入参；仅软删时 query 可为 null
     * @return 谓词列表
     */
    public static List<Predicate> collectPredicates(
        Root<PriceAdjustment> root,
        CriteriaBuilder cb,
        PriceAdjustmentDocumentListQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        if (query == null) {
            return predicates;
        }
        if (query.getOrgId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), query.getOrgId()));
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
        }
        if (StringUtils.isNotEmpty(query.getItemTable())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
        }
        if (query.getOperatorId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.OPERATOR_ID), query.getOperatorId()));
        }
        if (query.getApproverId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.APPROVER_ID), query.getApproverId()));
        }
        Date start = toDate(query.getStartDate());
        Date end = toDate(query.getEndDate());
        if (start != null || end != null) {
            // 列表时刻：已提交用 submit_time，草稿/未提交回落 adjustment_time（对齐进货单 apply_time / occurrence_time 分列展示）
            jakarta.persistence.criteria.Expression<Date> listTime =
                cb.coalesce(root.get(FieldNameConstants.SUBMIT_TIME), root.get(FieldNameConstants.ADJUSTMENT_TIME));
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(listTime, start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(listTime, end));
            }
        }
        Date adjustmentFrom = toDate(query.getAdjustmentTimeFrom());
        Date adjustmentTo = toDate(query.getAdjustmentTimeTo());
        if (adjustmentFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.ADJUSTMENT_TIME), adjustmentFrom));
        }
        if (adjustmentTo != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.ADJUSTMENT_TIME), adjustmentTo));
        }
        Date approveFrom = toDate(query.getApproveTimeFrom());
        Date approveTo = toDate(query.getApproveTimeTo());
        if (approveFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.APPROVE_TIME), approveFrom));
        }
        if (approveTo != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.APPROVE_TIME), approveTo));
        }
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
        }
        if (StringUtils.isNotEmpty(query.getBusNoLike())) {
            // 财务审批单号专用模糊（与 keyword 可 AND）
            String busLikeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getBusNoLike().trim());
            predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), busLikeIc));
        }
        return predicates;
    }

    /**
     * 调价单列表关键字：SQL 层「单号精确全等在前」（与 LIKE 列 busNo 同集合）。
     *
     * <p>优先使用 {@code keyword}；为空时回退 {@code busNoLike}（财务审批单号模糊）。</p>
     *
     * @param cq              CriteriaQuery；null 或 count 时工具内部跳过
     * @param root            调价主行根
     * @param cb              CriteriaBuilder
     * @param query           列表查询；无 keyword/busNoLike 时不写排序
     * @param secondaryOrders 次级排序（如列表时刻降序）；不可为 null
     */
    public static void applyKeywordExactFirstOrder(
        CriteriaQuery<?> cq,
        Root<PriceAdjustment> root,
        CriteriaBuilder cb,
        PriceAdjustmentDocumentListQuery query,
        List<Order> secondaryOrders) {
        if (query == null) {
            return;
        }
        String trimmed = null;
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            trimmed = query.getKeyword().trim();
        } else if (StringUtils.isNotEmpty(query.getBusNoLike())) {
            trimmed = query.getBusNoLike().trim();
        }
        if (StringUtils.isEmpty(trimmed)) {
            return;
        }
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
