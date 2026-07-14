/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.support;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestSettlementWindowQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SupplyRequest} 库房日结期间动账批量读的动态 WHERE 片段（**DM-WKF-006 §6.2.12**）。
 */
public final class SupplyRequestSettlementWindowPredicateSupport {

    private SupplyRequestSettlementWindowPredicateSupport() {
    }

    /**
     * 组装已同意供应行、审批时刻窗、库位命中、类型白名单与 Tab {@code item_table} 谓词。
     *
     * @param root  供应请求根
     * @param cb    条件构建器
     * @param query 日结窗入参；关键字段缺失时调用方应短路返回空列表
     * @return 谓词列表
     */
    public static List<Predicate> collectPredicates(
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        SupplyRequestSettlementWindowQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), SupplyStatus.AGREE.getCode()));
        if (query == null) {
            return predicates;
        }
        if (StringUtils.isNotEmpty(query.getItemTable())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
        }
        if (query.getTypeEnumWhitelist() != null && !query.getTypeEnumWhitelist().isEmpty()) {
            predicates.add(root.get(FieldNameConstants.TYPE_ENUM).in(query.getTypeEnumWhitelist()));
        }
        if (query.getTenantId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
        }
        if (query.getWarehouseId() != null) {
            Predicate sourceHit = cb.equal(root.get(FieldNameConstants.SOURCE_LOCATION_ID), query.getWarehouseId());
            Predicate purposeHit = cb.equal(root.get(FieldNameConstants.PURPOSE_LOCATION_ID), query.getWarehouseId());
            predicates.add(cb.or(sourceHit, purposeHit));
        }
        if (query.getApprovalWindowStartInclusive() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME), query.getApprovalWindowStartInclusive()));
        }
        if (query.getApprovalWindowEndInclusive() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME), query.getApprovalWindowEndInclusive()));
        }
        return predicates;
    }
}
