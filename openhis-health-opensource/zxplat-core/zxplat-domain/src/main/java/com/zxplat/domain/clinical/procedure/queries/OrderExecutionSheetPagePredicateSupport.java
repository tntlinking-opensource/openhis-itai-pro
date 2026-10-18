/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.queries;

import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.share.constants.FieldNameConstants;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.zxplat.framework.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 医嘱执行单列表过滤条件（仅 {@code cln_procedure} 根；跨请求表富化在应用层 {@code querysupport}）。
 */
public final class OrderExecutionSheetPagePredicateSupport {

    private OrderExecutionSheetPagePredicateSupport() {
    }

    /**
     * 收集执行单分页的 Procedure 根谓词。
     *
     * @param root 执行记录根
     * @param cb Criteria 构建器
     * @param query 分页入参
     * @return 谓词列表
     */
    public static List<Predicate> collectPagePredicates(
        Root<Procedure> root,
        CriteriaBuilder cb,
        OrderExecutionSheetPageQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        if (query == null) {
            return predicates;
        }
        if (query.getProcedureId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ID), query.getProcedureId()));
        }
        if (query.getRequestId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.REQUEST_ID), query.getRequestId()));
        }
        if (query.getEncounterId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
        }
        if (StringUtils.isNotEmpty(query.getCategoryEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_ENUM), query.getCategoryEnum().trim()));
        }
        if (query.getRefundId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.REFUND_ID), query.getRefundId()));
        }
        if (query.getStartTime() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.OCCURRENCE_TIME), query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.OCCURRENCE_TIME), query.getEndTime()));
        }
        return predicates;
    }
}
