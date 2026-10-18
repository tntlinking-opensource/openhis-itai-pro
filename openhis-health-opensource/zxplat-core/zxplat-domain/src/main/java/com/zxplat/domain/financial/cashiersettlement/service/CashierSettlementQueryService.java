/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.service;

import com.zxplat.domain.financial.cashiersettlement.entity.CashierSettlement;
import com.zxplat.domain.financial.cashiersettlement.queries.PageCashierSettlementApprovalQuery;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementApprovalListItemQueryModel;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementHeaderQueryModel;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementHistoryItemQueryModel;
import com.zxplat.domain.financial.cashiersettlement.repository.CashierSettlementRepository;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.framework.core.application.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
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
     * 按类型集合列出未软删交账头摘要（药房售价合计回刷批处理；仅本聚合）。
     *
     * @param typeEnums 类型 code 集合
     * @return 头表读模型列表；空入参返回空列表
     */
    public List<CashierSettlementHeaderQueryModel> listHeadersByTypeEnums(Collection<String> typeEnums) {
        if (typeEnums == null || typeEnums.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> distinctTypes = new LinkedHashSet<>();
        for (String typeEnum : typeEnums) {
            if (StringUtils.isNotEmpty(typeEnum)) {
                distinctTypes.add(typeEnum.trim());
            }
        }
        if (distinctTypes.isEmpty()) {
            return new ArrayList<>();
        }
        List<CashierSettlement> rows =
            this.cashierSettlementRepository.findByTypeEnumInAndIsDeletedFalse(distinctTypes);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<CashierSettlementHeaderQueryModel> headers = new ArrayList<>(rows.size());
        for (CashierSettlement settlement : rows) {
            if (settlement == null) {
                continue;
            }
            headers.add(this.toHeaderQueryModel(settlement));
        }
        return headers;
    }

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
     * 交账历史分页（不按药房收窄；收款日结等沿用）。
     *
     * @param typeEnum       OP_PAYMENT / IP_PAYMENT / OP_PHARMACY / IP_PHARMACY
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
        return this.pageHistory(typeEnum, submittedFrom, submittedTo, null, pageable);
    }

    /**
     * 交账历史分页；可选按 {@code location_id IN} 收窄（药房日结管辖药房）。
     *
     * <p>{@code locationIds == null}：不按药房过滤；
     * {@code locationIds} 非 null 且为空：无可见药房，直接空页（禁止退化为全院历史）。</p>
     *
     * @param typeEnum       OP_PAYMENT / IP_PAYMENT / OP_PHARMACY / IP_PHARMACY
     * @param submittedFrom  提交时刻起（含）
     * @param submittedTo    提交时刻止（含）
     * @param locationIds    药房主键集合；null 表示不限
     * @param pageable       分页
     * @return 历史页（按 {@code submitted_at} 降序）
     */
    public Page<CashierSettlementHistoryItemQueryModel> pageHistory(
        String typeEnum,
        LocalDateTime submittedFrom,
        LocalDateTime submittedTo,
        Collection<Long> locationIds,
        Pageable pageable) {
        if (StringUtils.isEmpty(typeEnum) || submittedFrom == null || submittedTo == null || pageable == null) {
            return Page.empty(pageable);
        }
        LinkedHashSet<Long> distinctLocationIds = null;
        if (locationIds != null) {
            distinctLocationIds = new LinkedHashSet<>();
            for (Long locationId : locationIds) {
                if (locationId != null) {
                    distinctLocationIds.add(locationId);
                }
            }
            // Gate：显式空集合 = 无可视药房，禁止退化为不加 location 谓词
            if (distinctLocationIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        LinkedHashSet<Long> locationFilter = distinctLocationIds;
        Specification<CashierSettlement> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), typeEnum));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.SUBMITTED_AT), submittedFrom));
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.SUBMITTED_AT), submittedTo));
            if (locationFilter != null) {
                predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationFilter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sortedPageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, FieldNameConstants.SUBMITTED_AT));
        Page<CashierSettlement> page = this.cashierSettlementRepository.findAll(specification, sortedPageable);
        List<CashierSettlementHistoryItemQueryModel> items = new ArrayList<>();
        for (CashierSettlement settlement : page.getContent()) {
            if (settlement == null) {
                continue;
            }
            items.add(this.toHistoryItemQueryModel(settlement));
        }
        return new PageImpl<>(items, sortedPageable, page.getTotalElements());
    }

    /**
     * 交账头实体 → 历史分页行读模型。
     *
     * @param settlement 交账头实体
     * @return 历史行；settlement 为空时返回 null
     */
    private CashierSettlementHistoryItemQueryModel toHistoryItemQueryModel(CashierSettlement settlement) {
        if (settlement == null) {
            return null;
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
        return item;
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

