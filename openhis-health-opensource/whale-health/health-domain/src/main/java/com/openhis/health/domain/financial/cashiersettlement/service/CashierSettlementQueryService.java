/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.service;

import com.openhis.health.domain.financial.cashiersettlement.entity.CashierSettlement;
import com.openhis.health.domain.financial.cashiersettlement.queries.PageCashierSettlementApprovalQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementApprovalListItemQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementHeaderQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementHistoryItemQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.repository.CashierSettlementRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.whale.core.application.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 收费员交账头读侧领域服务（DM-FIN-003 §5.2，仅本聚合）。
 *
 * <p>未交账 payment 候选预览在 {@code PaymentReconciliationQueryService}（B.6.4 禁止跨聚合 Repository）。</p>
 */
@Service
@RequiredArgsConstructor
public class CashierSettlementQueryService {

    private final CashierSettlementRepository cashierSettlementRepository;

    /**
     * 按交账头主键读取交账单号（提交成功后装配响应用）。
     *
     * @param settlementId 交账头主键
     * @return 交账单号；不存在时返回 {@code null}
     */
    public String findBusNoBySettlementId(Long settlementId) {
        CashierSettlement settlement = this.findEntityById(settlementId);
        // 无头则无法回显单号
        if (settlement == null) {
            return null;
        }
        return settlement.getBusNo();
    }

    /**
     * 读取交账头实体。
     *
     * @param settlementId 交账头主键
     * @return 实体；不存在返回 null
     */
    public CashierSettlement findEntityById(Long settlementId) {
        if (settlementId == null) {
            return null;
        }
        return this.cashierSettlementRepository.findById(settlementId).orElse(null);
    }

    /**
     * 交账历史分页。
     *
     * @param typeEnum       OP_PAYMENT / IP_PAYMENT 等
     * @param submittedFrom  提交时刻起
     * @param submittedTo    提交时刻止
     * @param pageable       分页
     * @return 历史页
     */
    public Page<CashierSettlementHistoryItemQueryModel> pageHistory(
        String typeEnum,
        LocalDateTime submittedFrom,
        LocalDateTime submittedTo,
        Pageable pageable) {
        if (StringUtils.isEmpty(typeEnum) || submittedFrom == null || submittedTo == null || pageable == null) {
            return Page.empty(pageable);
        }
        Page<CashierSettlement> page =
            this.cashierSettlementRepository.findByTypeEnumAndSubmittedAtBetweenAndIsDeletedFalseOrderBySubmittedAtDesc(
                typeEnum,
                submittedFrom,
                submittedTo,
                pageable);
        List<CashierSettlementHistoryItemQueryModel> items = new ArrayList<>();
        for (CashierSettlement settlement : page.getContent()) {
            if (settlement == null) {
                continue;
            }
            CashierSettlementHistoryItemQueryModel item = new CashierSettlementHistoryItemQueryModel();
            item.setSettlementId(settlement.getId());
            item.setBusNo(settlement.getBusNo());
            item.setTypeEnum(settlement.getTypeEnum());
            item.setSubmittedAt(settlement.getSubmittedAt());
            item.setDateFrom(settlement.getDateFrom());
            item.setDateTo(settlement.getDateTo());
            item.setPractitionerIds(settlement.getPractitionerIds());
            item.setSubmitterId(settlement.getSubmitterId());
            item.setApproverId(settlement.getApproverId());
            item.setApprovedAt(settlement.getApprovedAt());
            item.setOrgId(settlement.getOrgId());
            item.setLocationId(settlement.getLocationId());
            item.setPaymentCount(settlement.getPaymentCount());
            item.setTotalIncome(settlement.getTotalIncome());
            // 有挂载笔数即可按源表现算重打（DD：不存 print_snapshot_json）
            Integer paymentCount = settlement.getPaymentCount();
            boolean canReprint = paymentCount != null && paymentCount.intValue() > 0;
            item.setCanReprintFromMounts(canReprint);
            item.setStatusEnum(settlement.getStatusEnum());
            items.add(item);
        }
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    /**
     * 按主键读取交账头投影。
     *
     * @param settlementId 交账头主键
     * @return 头表读模型；不存在时返回 {@code null}
     */
    public CashierSettlementHeaderQueryModel findHeaderById(Long settlementId) {
        CashierSettlement settlement = this.findEntityById(settlementId);
        // 无头则无法装配详情
        if (settlement == null) {
            return null;
        }
        return this.toHeaderQueryModel(settlement);
    }

    /**
     * 交账审批列表分页。
     *
     * @param query    筛选条件；字段均可空表示不限
     * @param pageable 分页与排序
     * @return 审批列表页
     */
    public Page<CashierSettlementApprovalListItemQueryModel> pageForApproval(
        PageCashierSettlementApprovalQuery query,
        Pageable pageable) {
        if (pageable == null) {
            return Page.empty();
        }
        PageCashierSettlementApprovalQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new PageCashierSettlementApprovalQuery();
        }
        Specification<CashierSettlement> specification = this.buildApprovalPageSpecification(safeQuery);
        Page<CashierSettlement> page = this.cashierSettlementRepository.findAll(specification, pageable);
        List<CashierSettlementApprovalListItemQueryModel> items = new ArrayList<>();
        for (CashierSettlement settlement : page.getContent()) {
            if (settlement == null) {
                continue;
            }
            items.add(this.toApprovalListItemQueryModel(settlement));
        }
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    /**
     * 构建审批列表 Specification（默认排除软删）。
     *
     * @param query 筛选条件
     * @return JPA 谓词
     */
    private Specification<CashierSettlement> buildApprovalPageSpecification(PageCashierSettlementApprovalQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 类型收窄：空列表表示不限
            if (query.getTypeEnums() != null && !query.getTypeEnums().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.TYPE_ENUM).in(query.getTypeEnums()));
            }
            // 状态收窄：空列表表示不限
            if (query.getStatusEnums() != null && !query.getStatusEnums().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(query.getStatusEnums()));
            }
            // 交账提交时间窗：仅匹配 submitted_at 列
            if (query.getSubmittedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), query.getSubmittedFrom()));
            }
            if (query.getSubmittedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), query.getSubmittedTo()));
            }
            if (query.getOrgId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), query.getOrgId()));
            }
            if (query.getLocationId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
            }
            if (!StringUtils.isEmpty(query.getBusNoKeyword())) {
                String pattern = "%" + query.getBusNoKeyword().trim() + "%";
                predicates.add(cb.like(root.get(FieldNameConstants.BUS_NO), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 实体 → 审批列表行读模型。
     *
     * @param settlement 交账头实体
     * @return 列表行
     */
    private CashierSettlementApprovalListItemQueryModel toApprovalListItemQueryModel(CashierSettlement settlement) {
        CashierSettlementApprovalListItemQueryModel item = new CashierSettlementApprovalListItemQueryModel();
        item.setSettlementId(settlement.getId());
        item.setBusNo(settlement.getBusNo());
        item.setTypeEnum(settlement.getTypeEnum());
        item.setStatusEnum(settlement.getStatusEnum());
        item.setOrgId(settlement.getOrgId());
        item.setLocationId(settlement.getLocationId());
        item.setDateFrom(settlement.getDateFrom());
        item.setDateTo(settlement.getDateTo());
        item.setPaymentCount(settlement.getPaymentCount());
        item.setTotalIncome(settlement.getTotalIncome());
        item.setSubmitterId(settlement.getSubmitterId());
        item.setSubmittedAt(settlement.getSubmittedAt());
        item.setApproverId(settlement.getApproverId());
        item.setApprovedAt(settlement.getApprovedAt());
        return item;
    }

    /**
     * 实体 → 头表读模型。
     *
     * @param settlement 交账头实体
     * @return 头表读模型
     */
    private CashierSettlementHeaderQueryModel toHeaderQueryModel(CashierSettlement settlement) {
        CashierSettlementHeaderQueryModel header = new CashierSettlementHeaderQueryModel();
        header.setSettlementId(settlement.getId());
        header.setBusNo(settlement.getBusNo());
        header.setTypeEnum(settlement.getTypeEnum());
        header.setStatusEnum(settlement.getStatusEnum());
        header.setOrgId(settlement.getOrgId());
        header.setLocationId(settlement.getLocationId());
        header.setDateFrom(settlement.getDateFrom());
        header.setDateTo(settlement.getDateTo());
        header.setPaymentCount(settlement.getPaymentCount());
        header.setTotalIncome(settlement.getTotalIncome());
        header.setPractitionerIds(settlement.getPractitionerIds());
        header.setSubmitterId(settlement.getSubmitterId());
        header.setSubmittedAt(settlement.getSubmittedAt());
        header.setApproverId(settlement.getApproverId());
        header.setApprovedAt(settlement.getApprovedAt());
        return header;
    }

    /**
     * 审批列表默认排序：交账时间倒序、主键倒序。
     *
     * @return Spring Sort
     */
    public Sort defaultApprovalPageSort() {
        return Sort.by(
            Sort.Order.desc("submittedAt"),
            Sort.Order.desc(FieldNameConstants.ID));
    }
}
