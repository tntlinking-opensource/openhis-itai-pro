/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.support;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 库房单据明细列表过滤条件（查 {@code wkf_supply_request}；与 RM 分页 Specification 语义对齐）。
 */
public final class WarehouseDocumentDetailPagePredicateSupport {

    private WarehouseDocumentDetailPagePredicateSupport() {
    }

    /**
     * 构建与分页列表相同的 WHERE 片段，并在有关键字时写入「精确优先」ORDER BY（B.2.1）。
     *
     * @param root  供应请求根
     * @param cq    CriteriaQuery；count 查询由工具内部跳过 orderBy；可为 null（仅谓词）
     * @param cb    条件构建器
     * @param query 查询入参；为 null 时仅返回软删谓词
     * @return 谓词列表
     */
    public static List<Predicate> collectPagePredicates(
        Root<SupplyRequest> root,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        WarehouseDocumentDetailPageQuery query) {
        List<Predicate> predicates = collectPagePredicates(root, cb, query);
        applyKeywordExactFirstOrder(cq, root, cb, query);
        return predicates;
    }

    /**
     * 构建与分页列表相同的 WHERE 片段（不含 ORDER BY）。
     *
     * @param root  供应请求根
     * @param cb    条件构建器
     * @param query 查询入参；为 null 时仅返回软删谓词
     * @return 谓词列表
     */
    public static List<Predicate> collectPagePredicates(
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        WarehouseDocumentDetailPageQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        if (query == null) {
            return predicates;
        }
        if (StringUtils.isNotEmpty(query.getBusNo())) {
            // 单据号支持模糊：与报表页「单据号」检索习惯一致（完整号仍可精确命中）
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getBusNo().trim());
            predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
        }
        if (StringUtils.isNotEmpty(query.getTypeEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), query.getTypeEnum().trim()));
        } else if (query.getTypeEnums() != null) {
            List<String> typeCodes = new ArrayList<>();
            for (String code : query.getTypeEnums()) {
                if (StringUtils.isNotEmpty(code)) {
                    typeCodes.add(code.trim());
                }
            }
            if (typeCodes.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.TYPE_ENUM).in(typeCodes));
            }
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
        } else if (query.getStatusEnums() != null) {
            // 多状态 IN：与 typeEnums 同构；空集合 → 无命中
            List<String> statusCodes = new ArrayList<>();
            for (String code : query.getStatusEnums()) {
                if (StringUtils.isNotEmpty(code)) {
                    statusCodes.add(code.trim());
                }
            }
            if (statusCodes.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusCodes));
            }
        }
        if (query.getTenantId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
        }
        if (query.getSourceLocationIds() != null) {
            Collection<Long> ids = query.getSourceLocationIds();
            if (ids.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.SOURCE_LOCATION_ID).in(ids));
            }
        } else if (query.getSourceLocationId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_LOCATION_ID), query.getSourceLocationId()));
        }
        if (query.getPurposeLocationIds() != null) {
            Collection<Long> purposeIds = query.getPurposeLocationIds();
            if (purposeIds.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.PURPOSE_LOCATION_ID).in(purposeIds));
            }
        } else if (query.getPurposeLocationId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.PURPOSE_LOCATION_ID), query.getPurposeLocationId()));
        }
        if (query.getItemId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), query.getItemId()));
        }
        // 批号：关键字 LIKE 优先于精确匹配（AS-INV-RPT-002 · BR-INV-DDC-006）
        if (StringUtils.isNotEmpty(query.getLotNumberKeyword())) {
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getLotNumberKeyword().trim());
            predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), likeIc));
        } else if (StringUtils.isNotEmpty(query.getLotNumber())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NUMBER), query.getLotNumber().trim()));
        }
        // deliveryStatusEnum 为 RM 发放侧列；主表 wkf_supply_request 无对应列，当前三处调用方均未传入，故忽略。
        if (query.getApplyTimeStart() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getApplyTimeStart()));
        }
        if (query.getApplyTimeEnd() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getApplyTimeEnd()));
        }
        if (query.getApprovalTimeStart() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME), query.getApprovalTimeStart()));
        }
        if (query.getApprovalTimeEnd() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME), query.getApprovalTimeEnd()));
        }
        // 品名/编码关键字：应用层预解析 item_table+item_id（B.6.4，禁止 Criteria 跨包 JOIN 定义表）
        if (query.getResolvedMedicationDefinitionIdsForKeyword() != null
            || query.getResolvedDeviceDefinitionIdsForKeyword() != null) {
            List<Predicate> keywordOr = new ArrayList<>();
            if (query.getResolvedMedicationDefinitionIdsForKeyword() != null
                && !query.getResolvedMedicationDefinitionIdsForKeyword().isEmpty()) {
                keywordOr.add(medicationDefinitionItemPredicate(
                    root, cb, query.getResolvedMedicationDefinitionIdsForKeyword()));
            }
            if (query.getResolvedDeviceDefinitionIdsForKeyword() != null
                && !query.getResolvedDeviceDefinitionIdsForKeyword().isEmpty()) {
                keywordOr.add(deviceDefinitionItemPredicate(
                    root, cb, query.getResolvedDeviceDefinitionIdsForKeyword()));
            }
            if (keywordOr.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(cb.or(keywordOr.toArray(new Predicate[0])));
            }
        }
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc);
            Predicate reasonLike = cb.like(cb.lower(root.get(FieldNameConstants.REASON)), likeIc);
            Predicate remakeLike = cb.like(cb.lower(root.get(FieldNameConstants.REMAKE)), likeIc);
            predicates.add(cb.or(busNoLike, reasonLike, remakeLike));
        }
        return predicates;
    }

    /**
     * 单据明细关键字：SQL 层「精确全等在前」（与 LIKE 列 busNo/reason/remake 同集合）；次级审批时间倒序。
     *
     * <p>批号关键字仅作过滤，不参与精确优先（与主 keyword 扫列不同）。</p>
     *
     * @param cq    CriteriaQuery；null 或 count 时跳过
     * @param root  供应请求根
     * @param cb    CriteriaBuilder
     * @param query 分页查询；无主 keyword 时不写排序
     */
    public static void applyKeywordExactFirstOrder(
        CriteriaQuery<?> cq,
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        WarehouseDocumentDetailPageQuery query) {
        if (query == null || StringUtils.isEmpty(query.getKeyword())) {
            return;
        }
        String trimmed = query.getKeyword().trim();
        List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.APPROVAL_TIME)));
        KeywordExactFirstOrderSupport.applyExactFirstOrder(
            cq,
            cb,
            trimmed,
            secondary,
            root.get(FieldNameConstants.BUS_NO),
            root.get(FieldNameConstants.REASON),
            root.get(FieldNameConstants.REMAKE));
    }

    /**
     * 药品行谓词：{@code item_table} 与 {@code item_id IN} 组合，避免跨表 id 误命中。
     */
    private static Predicate medicationDefinitionItemPredicate(
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        List<Long> definitionIds) {
        return cb.and(
            cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.MED_MEDICATION_DEFINITION),
            root.get(FieldNameConstants.ITEM_ID).in(definitionIds));
    }

    /**
     * 耗材行谓词：{@code item_table} 与 {@code item_id IN} 组合。
     */
    private static Predicate deviceDefinitionItemPredicate(
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        List<Long> definitionIds) {
        return cb.and(
            cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.ADM_DEVICE_DEFINITION),
            root.get(FieldNameConstants.ITEM_ID).in(definitionIds));
    }
}
