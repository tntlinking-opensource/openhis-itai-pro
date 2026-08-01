/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.support;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SupplyRequest}（{@code wkf_supply_request}）单据列表的动态 WHERE 片段，供各业务类型列表复用。
 *
 * <p>与 {@link SupplyRequestDocumentListQuery} 对齐：关键字、时段取自 {@link com.openhis.health.domain.share.query.AbstractPageQuery}
 *（B.10.11 / B.2.1）。</p>
 */
public final class SupplyRequestDocumentListPredicateSupport {

    private SupplyRequestDocumentListPredicateSupport() {
    }

    /**
     * 组装软删、类型、状态、租户、源/目的位置、申请时刻窗（{@code startDate}/{@code endDate}）、
     * 制单/审批时刻窗、制单人/审批人、品类、关键字（单号/原因/备注）与单号专用模糊谓词。
     *
     * @param root  供应请求根
     * @param cb    条件构建器
     * @param query 过滤入参；仅软删时 query 可为 null
     * @return 谓词列表（调用方以 AND 或数组交给 {@code CriteriaQuery#where}）
     */
    public static List<Predicate> collectPredicates(
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        SupplyRequestDocumentListQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        // 供应写表列表与统计均须排除逻辑删除行，与底座默认口径一致。
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        if (query == null) {
            return predicates;
        }
        // 类型：单值等值优先于多选 IN（与 WarehouseDocumentDetailPageQuery 同构）。
        if (StringUtils.isNotEmpty(query.getTypeEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), query.getTypeEnum().trim()));
        } else if (query.getTypeEnums() != null) {
            List<String> typeCodes = new ArrayList<>();
            for (String code : query.getTypeEnums()) {
                if (StringUtils.isNotEmpty(code)) {
                    typeCodes.add(code.trim());
                }
            }
            // 显式传空集合：无匹配类型，结果须为空（禁止退化为不过滤类型）。
            if (typeCodes.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.TYPE_ENUM).in(typeCodes));
            }
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
        }
        if (query.getTenantId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
        }
        if (query.getLocationId() != null) {
            // 财务审批统一库房筛：源库或目的库任一命中即可（进货/损益偏目的，退货偏源）
            predicates.add(cb.or(
                cb.equal(root.get(FieldNameConstants.SOURCE_LOCATION_ID), query.getLocationId()),
                cb.equal(root.get(FieldNameConstants.PURPOSE_LOCATION_ID), query.getLocationId())));
        } else {
            if (query.getSourceLocationIds() != null && !query.getSourceLocationIds().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.SOURCE_LOCATION_ID).in(query.getSourceLocationIds()));
            } else if (query.getSourceLocationId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_LOCATION_ID), query.getSourceLocationId()));
            }
            if (query.getPurposeLocationIds() != null && !query.getPurposeLocationIds().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.PURPOSE_LOCATION_ID).in(query.getPurposeLocationIds()));
            } else if (query.getPurposeLocationId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PURPOSE_LOCATION_ID), query.getPurposeLocationId()));
            }
        }
        if (query.getSupplierId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.SUPPLIER_ID), query.getSupplierId()));
        }
        if (query.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getStartDate()));
        }
        if (query.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getEndDate()));
        }
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            // 用户关键字：列侧 lower + 模式侧 SearchCodeUtil，禁止手写 % 拼接（B.2.1）。
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc);
            Predicate reasonLike = cb.like(cb.lower(root.get(FieldNameConstants.REASON)), likeIc);
            Predicate remakeLike = cb.like(cb.lower(root.get(FieldNameConstants.REMAKE)), likeIc);
            predicates.add(cb.or(busNoLike, reasonLike, remakeLike));
        }
        if (query.getBusNoIn() != null && !query.getBusNoIn().isEmpty()) {
            // 住院汇总发药：应用层按发放表门控写入 busNo 白名单
            predicates.add(root.get(FieldNameConstants.BUS_NO).in(query.getBusNoIn()));
        }
        if (StringUtils.isNotEmpty(query.getBusNoLike())) {
            // 财务审批等：单号专用模糊，不扫原因/备注列
            String busLikeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getBusNoLike().trim());
            predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), busLikeIc));
        }
        if (query.getApplicantId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.APPLICANT_ID), query.getApplicantId()));
        }
        if (query.getApproverId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.APPROVER_ID), query.getApproverId()));
        }
        if (StringUtils.isNotEmpty(query.getItemTable())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
        }
        if (query.getOccurrenceTimeFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get(FieldNameConstants.OCCURRENCE_TIME), query.getOccurrenceTimeFrom()));
        }
        if (query.getOccurrenceTimeTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get(FieldNameConstants.OCCURRENCE_TIME), query.getOccurrenceTimeTo()));
        }
        if (query.getApprovalTimeFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME), query.getApprovalTimeFrom()));
        }
        if (query.getApprovalTimeTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME), query.getApprovalTimeTo()));
        }
        return predicates;
    }
}
