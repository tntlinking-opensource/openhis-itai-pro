/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.service;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustmentDetail;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentApprovedLineListQuery;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDetailQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentImpactSummaryQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.repository.PriceAdjustmentDetailRepository;
import com.openhis.health.domain.custom.priceadjustment.repository.PriceAdjustmentRepository;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentApprovedLineListPredicateSupport;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentDocumentListPredicateSupport;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 价格调整读侧查询服务（DM-CUS-011 v2 单据模型）。
 *
 * <p>聚合边界：仅本聚合 {@link PriceAdjustmentRepository} / {@link PriceAdjustmentDetailRepository}。</p>
 * <p>通过 {@code adjustmentStyle} 区分诊疗一对一与药品耗材批号展示；跨聚合读由应用层编排。</p>
 */
@Service
@RequiredArgsConstructor
public class PriceAdjustmentQueryService {

    private final PriceAdjustmentRepository repository;
    private final PriceAdjustmentDetailRepository detailRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 按调价单号加载整单读模型（主行 + 子表批号明细）。
     *
     * @param busNo 调价单号
     * @return 单据读模型
     * @throws BusinessException 单号为空或记录不存在时
     */
    public PriceAdjustmentDocumentQueryModel getDocumentByBusNo(String busNo) {
        List<PriceAdjustment> lines = this.requireLines(busNo);
        return this.toDocumentModel(busNo, lines);
    }

    /**
     * 待审批调价单分页（按 {@code bus_no} 聚合，审批工作台）。
     *
     * @param orgId    机构；{@code null} 表示全院
     * @param pageable 分页
     * @return 单据分页
     */
    public Page<PriceAdjustmentDocumentQueryModel> listPendingDocuments(Long orgId, Pageable pageable) {
        List<PriceAdjustment> pendingRows = repository.findByStatusEnumAndIsDeletedFalseOrderBySubmitTimeAsc(
            PriceAdjustmentStatus.PENDING.getCode()
        );
        Map<String, List<PriceAdjustment>> grouped = this.groupByBusNo(pendingRows);
        List<String> busNos = new ArrayList<>(grouped.keySet());
        if (orgId != null) {
            // 机构过滤：取锚点行 org_id 与入参一致的单据
            List<String> filtered = new ArrayList<>();
            for (String busNo : busNos) {
                List<PriceAdjustment> lines = grouped.get(busNo);
                if (lines != null && !lines.isEmpty() && orgId.equals(lines.get(0).getOrgId())) {
                    filtered.add(busNo);
                }
            }
            busNos = filtered;
        }
        return this.pageDocuments(busNos, grouped, pageable);
    }

    /**
     * 判断项目是否存在待审批调价单（临床阻断入口 / 提交互斥 BR-PRC-SUB-002）。
     *
     * @param itemTable 项目物理表
     * @param itemId    项目定义主键
     * @return 存在 pending 调价单时为 true；参数非法时 false
     */
    public Boolean existsPendingByItem(String itemTable, Long itemId) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            return false;
        }
        return repository.existsByItemTableAndItemIdAndStatusEnumAndIsDeletedFalse(
            itemTable.trim(),
            itemId,
            PriceAdjustmentStatus.PENDING.getCode()
        );
    }

    /**
     * 批量判断同表项目集合是否存在 pending 调价（BR-PRC-LOCK-007 签发前互斥）。
     *
     * @param itemTable 项目物理表
     * @param itemIds   项目定义主键集合；空则 false
     * @return 任一项目存在 pending 调价单时为 true
     */
    public boolean existsPendingByAnyItem(String itemTable, Collection<Long> itemIds) {
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return false;
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long itemId : itemIds) {
            if (itemId != null) {
                distinctIds.add(itemId);
            }
        }
        if (distinctIds.isEmpty()) {
            return false;
        }
        return this.repository.existsByItemTableAndItemIdInAndStatusEnumAndIsDeletedFalse(
            itemTable.trim(),
            distinctIds,
            PriceAdjustmentStatus.PENDING.getCode());
    }

    /**
     * 判断单品是否存在已审批调价（报表进价是否需回放调价记录）。
     *
     * @param itemTable 项目物理表
     * @param itemId    项目定义主键
     * @return 存在已审批调价时为 true
     */
    public boolean existsApprovedByItem(String itemTable, Long itemId) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            return false;
        }
        return this.repository.existsByItemTableAndItemIdAndStatusEnumAndIsDeletedFalse(
            itemTable.trim(),
            itemId,
            PriceAdjustmentStatus.APPROVED.getCode());
    }

    /**
     * 批量筛出存在已审批调价的项目主键（一次 IN，避免报表逐品规 exists）。
     *
     * <p>仅需品规级判断时用本方法；报表历史进价须用 {@link #listApprovedItemLotCompositeKeys}
     * 再与结果批号 map 匹配，禁止无脑对全品规拉完整调价历史。</p>
     *
     * @param itemTable 项目物理表
     * @param itemIds   项目主键集合
     * @return 有已审批调价的 itemId；空入参返回空集
     */
    public LinkedHashSet<Long> listApprovedItemIds(String itemTable, Collection<Long> itemIds) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long itemId : itemIds) {
            if (itemId != null) {
                distinct.add(itemId);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<PriceAdjustment> rows = this.repository.findByItemTableAndItemIdInAndStatusEnumAndIsDeletedFalse(
            itemTable.trim(),
            new ArrayList<>(distinct),
            PriceAdjustmentStatus.APPROVED.getCode());
        if (rows == null) {
            return result;
        }
        for (PriceAdjustment row : rows) {
            if (row != null && row.getItemId() != null) {
                result.add(row.getItemId());
            }
        }
        return result;
    }

    /**
     * 轻量：单独查本聚合已审批调价，拎出「品规+批号」复合键（主表 IN + 子表 IN）。
     *
     * <p>不组装完整 {@link PriceAdjustmentLineQueryModel}；调用方用代码与结果行批号 map 匹配后，
     * 仅对命中品规再调 {@link #findApprovedHistoryByItems}。禁止把调价表 JOIN 进主业务大扫。</p>
     *
     * @param itemTable 项目物理表
     * @param itemIds   候选品规主键；空则返回空集
     * @return {@link #itemLotCompositeKey} 集合；无调价明细时为空
     */
    public LinkedHashSet<String> listApprovedItemLotCompositeKeys(
        String itemTable,
        Collection<Long> itemIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long itemId : itemIds) {
            // 空主键不进 IN
            if (itemId != null) {
                distinct.add(itemId);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<PriceAdjustment> rows = this.repository.findByItemTableAndItemIdInAndStatusEnumAndIsDeletedFalse(
            itemTable.trim(),
            new ArrayList<>(distinct),
            PriceAdjustmentStatus.APPROVED.getCode());
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Map<Long, Long> adjustmentIdToItemId = new LinkedHashMap<>();
        List<Long> adjustmentIds = new ArrayList<>();
        for (PriceAdjustment row : rows) {
            // Gate：缺主行主键或品规 id 无法组批号键
            if (row == null || row.getId() == null || row.getItemId() == null) {
                continue;
            }
            adjustmentIdToItemId.put(row.getId(), row.getItemId());
            adjustmentIds.add(row.getId());
        }
        if (adjustmentIds.isEmpty()) {
            return result;
        }
        List<PriceAdjustmentDetail> details =
            this.detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);
        if (details == null || details.isEmpty()) {
            return result;
        }
        for (PriceAdjustmentDetail detail : details) {
            // Gate：明细批号空则无法与发放/账单批号匹配
            if (detail == null || detail.getAdjustmentId() == null || StringUtils.isEmpty(detail.getLotNumber())) {
                continue;
            }
            Long itemId = adjustmentIdToItemId.get(detail.getAdjustmentId());
            if (itemId == null) {
                continue;
            }
            result.add(this.itemLotCompositeKey(itemId, detail.getLotNumber()));
        }
        return result;
    }

    /**
     * 组「品规+批号」复合键（SOH 分隔，与批号定价键同门禁，避免批号字面量碰撞）。
     *
     * @param itemId    品规主键
     * @param lotNumber 批号（会 trim）
     * @return 复合键；任一项非法时 {@code null}
     */
    public String itemLotCompositeKey(Long itemId, String lotNumber) {
        if (itemId == null || StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        return itemId + "\u0001" + lotNumber.trim();
    }

    /**
     * 调价单列表：统计不重复 {@code bus_no}（与 {@link #listDistinctBusNoPage} 过滤一致，对齐进货单列表）。
     *
     * @param query 列表收窄；允许为 null（仅软删基线）
     * @return 聚合后单据条数
     */
    public long countDistinctBusNo(PriceAdjustmentDocumentListQuery query) {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<PriceAdjustment> root = cq.from(PriceAdjustment.class);
        cq.select(cb.countDistinct(root.get(FieldNameConstants.BUS_NO)));
        cq.where(PriceAdjustmentDocumentListPredicateSupport.collectPredicates(root, cb, query)
            .toArray(new Predicate[0]));
        Long n = this.entityManager.createQuery(cq).getSingleResult();
        if (n == null) {
            return 0L;
        }
        return n.longValue();
    }

    /**
     * 调价单列表：按 {@code bus_no} 分组分页（组排序键 {@code COALESCE(submit_time, adjustment_time)} 降序）。
     *
     * @param query 列表收窄与分页；null 时返回空列表
     * @return 当前页业务单号
     */
    public List<String> listDistinctBusNoPage(PriceAdjustmentDocumentListQuery query) {
        if (query == null) {
            return Collections.emptyList();
        }
        int pageSize = query.getBoundedPageSize();
        if (pageSize < 1) {
            return Collections.emptyList();
        }
        int firstResult = query.springPageIndex() * pageSize;
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<PriceAdjustment> root = cq.from(PriceAdjustment.class);
        cq.select(root.get(FieldNameConstants.BUS_NO));
        cq.where(PriceAdjustmentDocumentListPredicateSupport.collectPredicates(root, cb, query)
            .toArray(new Predicate[0]));
        cq.groupBy(root.get(FieldNameConstants.BUS_NO));
        jakarta.persistence.criteria.Expression<Date> listTime =
            cb.coalesce(root.get(FieldNameConstants.SUBMIT_TIME), root.get(FieldNameConstants.ADJUSTMENT_TIME));
        List<jakarta.persistence.criteria.Order> secondary =
            List.of(cb.desc(cb.greatest(listTime)));
        // B.2.1：有 keyword/busNoLike 时精确全等单号置顶，次级仍按列表时刻降序
        boolean hasFuzzyBusNo = StringUtils.isNotEmpty(query.getKeyword())
            || StringUtils.isNotEmpty(query.getBusNoLike());
        if (hasFuzzyBusNo) {
            PriceAdjustmentDocumentListPredicateSupport.applyKeywordExactFirstOrder(
                cq, root, cb, query, secondary);
        } else {
            cq.orderBy(secondary);
        }
        TypedQuery<String> tq = this.entityManager.createQuery(cq);
        tq.setFirstResult(firstResult);
        tq.setMaxResults(pageSize);
        return tq.getResultList();
    }

    /**
     * 调价单列表：在相同过滤条件下加载给定单号集合下的全部主行（供按单聚合装配）。
     *
     * @param filter 与列表统计一致的收窄；可为 null
     * @param busNos 当前页单号；空时返回空列表
     * @return 主行实体列表
     */
    public List<PriceAdjustment> listLinesByBusNosWithFilter(PriceAdjustmentDocumentListQuery filter, List<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<PriceAdjustment> spec = (root, cq, cb) -> {
            List<Predicate> predicates = PriceAdjustmentDocumentListPredicateSupport.collectPredicates(root, cb, filter);
            predicates.add(root.get(FieldNameConstants.BUS_NO).in(busNos));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.SUBMIT_TIME),
            Sort.Order.desc(FieldNameConstants.ADJUSTMENT_TIME)
        );
        return this.repository.findAll(spec, sort);
    }

    /**
     * 按单号列表组装单据读模型（批量 IN 子表）。
     *
     * @param busNos 单号列表
     * @param groupedLines 预加载主行（须已按单号分组或平铺）
     * @return 读模型列表（顺序与 busNos 一致）
     */
    public List<PriceAdjustmentDocumentQueryModel> listDocumentsByBusNos(
        List<String> busNos,
        List<PriceAdjustment> groupedLines
    ) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<PriceAdjustment>> grouped = this.groupByBusNo(groupedLines);
        List<PriceAdjustmentDocumentQueryModel> documents = new ArrayList<>();
        for (String busNo : busNos) {
            documents.add(this.toDocumentModel(busNo, grouped.get(busNo)));
        }
        return documents;
    }

    /**
     * 汇总子表盈亏合计（SUM detail.purchase_impact / selling_impact）。
     *
     * @param busNo 调价单号
     * @return 盈亏汇总读模型
     * @throws BusinessException 单号无效或记录不存在时
     */
    public PriceAdjustmentImpactSummaryQueryModel sumImpactByBusNo(String busNo) {
        List<PriceAdjustment> lines = this.requireLines(busNo);
        List<Long> adjustmentIds = new ArrayList<>();
        for (PriceAdjustment line : lines) {
            adjustmentIds.add(line.getId());
        }
        List<PriceAdjustmentDetail> details = detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);

        BigDecimal totalPurchase = BigDecimal.ZERO;
        BigDecimal totalSelling = BigDecimal.ZERO;
        // 内存累加子表盈亏（已提交固化的 impact 字段）
        for (PriceAdjustmentDetail detail : details) {
            if (detail.getPurchaseImpact() != null) {
                totalPurchase = totalPurchase.add(detail.getPurchaseImpact());
            }
            if (detail.getSellingImpact() != null) {
                totalSelling = totalSelling.add(detail.getSellingImpact());
            }
        }

        PriceAdjustmentImpactSummaryQueryModel model = new PriceAdjustmentImpactSummaryQueryModel();
        model.setBusNo(busNo.trim());
        model.setTotalPurchaseImpact(totalPurchase);
        model.setTotalSellingImpact(totalSelling);
        return model;
    }

    /**
     * 按项目查询已生效调价历史（审批时间倒序）。
     *
     * <p>单品入口；批量报表须用 {@link #findApprovedHistoryByItems}，且须先
     * {@link #listApprovedItemLotCompositeKeys} 与结果批号匹配收窄——<strong>禁止</strong>对无调价/未命中品规拉历史。</p>
     *
     * @param itemTable 项目物理表
     * @param itemId    项目定义主键
     * @return 主行读模型列表（含子表）；参数非法时空列表
     */
    public List<PriceAdjustmentLineQueryModel> findApprovedHistoryByItem(String itemTable, Long itemId) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            return List.of();
        }
        Map<Long, List<PriceAdjustmentLineQueryModel>> byItem =
            this.findApprovedHistoryByItems(itemTable, List.of(itemId));
        List<PriceAdjustmentLineQueryModel> history = byItem.get(itemId);
        if (history == null) {
            return List.of();
        }
        return history;
    }

    /**
     * 批量按项目加载已生效调价历史（一次主表 IN + 子表批量；审批时刻倒序）。
     *
     * <p>调用方须先 {@link #listApprovedItemLotCompositeKeys}（或 {@link #listApprovedItemIds}）
     * 仅传入<strong>与结果批号命中</strong>的 itemId；无调价/未命中品规不得调用本方法（直接用 ACTIVE 批号进/售价）。</p>
     *
     * @param itemTable 项目物理表
     * @param itemIds   已确认有调价且批号命中的项目主键；空则返回空 Map
     * @return itemId → 调价主行读模型列表（含子表）；无命中品规不出现在 Map
     */
    public Map<Long, List<PriceAdjustmentLineQueryModel>> findApprovedHistoryByItems(
        String itemTable,
        Collection<Long> itemIds) {
        Map<Long, List<PriceAdjustmentLineQueryModel>> result = new LinkedHashMap<>();
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long itemId : itemIds) {
            if (itemId != null) {
                distinct.add(itemId);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<PriceAdjustment> rows = this.repository.findByItemTableAndItemIdInAndStatusEnumAndIsDeletedFalse(
            itemTable.trim(),
            new ArrayList<>(distinct),
            PriceAdjustmentStatus.APPROVED.getCode());
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        // 审批时刻倒序（与单品 OrderByApproveTimeDesc 同口径）
        rows.sort((left, right) -> {
            Date leftTime = null;
            Date rightTime = null;
            if (left != null) {
                leftTime = left.getApproveTime();
            }
            if (right != null) {
                rightTime = right.getApproveTime();
            }
            if (leftTime == null && rightTime == null) {
                return 0;
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });
        List<PriceAdjustmentLineQueryModel> models = this.toLineModels(rows);
        for (PriceAdjustmentLineQueryModel model : models) {
            if (model == null || model.getItemId() == null) {
                continue;
            }
            result.computeIfAbsent(model.getItemId(), ignored -> new ArrayList<>()).add(model);
        }
        return result;
    }

    /**
     * 按库位汇总期间已审批调价进价盈亏（库房期初期末明细平账，BR-INV-SET-012 调价项）。
     *
     * <p>仅合计子表 {@code location_id} 与入参库位相等且非空的行；loc 空（诊疗/历史无库位）不计入本库。</p>
     * <p>本方法仅读本聚合 Repository，禁止跨聚合。</p>
     *
     * @param itemTable              物品主表
     * @param itemIds                品种主键集合；空或 null 表示不按品种收窄（同表同库期间全量）
     * @param locationId             库位主键；空则返回空 Map
     * @param approveTimeFromInclusive 审批时刻下界（含）；可空表示不限下界
     * @param approveTimeToInclusive   审批时刻上界（含）；可空表示不限上界
     * @return itemId → 进价盈亏合计；无命中时为空 Map
     */
    public Map<Long, BigDecimal> sumApprovedPurchaseImpactByLocation(
        String itemTable,
        Collection<Long> itemIds,
        Long locationId,
        LocalDateTime approveTimeFromInclusive,
        LocalDateTime approveTimeToInclusive
    ) {
        Map<Long, BigDecimal> result = new HashMap<>();
        // Gate：缺库位或物品表时无法按库平账
        if (locationId == null || StringUtils.isEmpty(itemTable)) {
            return result;
        }
        LinkedHashSet<Long> distinctItemIds = new LinkedHashSet<>();
        if (itemIds != null) {
            for (Long itemId : itemIds) {
                // 空主键不入过滤集
                if (itemId != null) {
                    distinctItemIds.add(itemId);
                }
            }
        }
        Date fromDate = null;
        // 审批窗下界：LocalDateTime → Date，供 JPA 与实体 approveTime 比较
        if (approveTimeFromInclusive != null) {
            fromDate = Timestamp.valueOf(approveTimeFromInclusive);
        }
        Date toDate = null;
        // 审批窗上界：同上
        if (approveTimeToInclusive != null) {
            toDate = Timestamp.valueOf(approveTimeToInclusive);
        }
        final Date fromBound = fromDate;
        final Date toBound = toDate;
        final boolean filterByItemIds = !distinctItemIds.isEmpty();
        String table = itemTable.trim();
        Specification<PriceAdjustment> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), table));
            // 有品种集时收窄；空集表示同表期间全量（日结装载）
            if (filterByItemIds) {
                predicates.add(root.get(FieldNameConstants.ITEM_ID).in(distinctItemIds));
            }
            predicates.add(
                cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PriceAdjustmentStatus.APPROVED.getCode()));
            // 审批窗下界：approve_time >= from
            if (fromBound != null) {
                predicates.add(
                    cb.greaterThanOrEqualTo(root.get(FieldNameConstants.APPROVE_TIME), fromBound));
            }
            // 审批窗上界：approve_time <= to
            if (toBound != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.APPROVE_TIME), toBound));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<PriceAdjustment> lines = this.repository.findAll(specification);
        // 期间无已审批调价主行：本库盈亏为 0
        if (lines.isEmpty()) {
            return result;
        }
        Map<Long, Long> adjustmentIdToItemId = new HashMap<>();
        List<Long> adjustmentIds = new ArrayList<>();
        for (PriceAdjustment line : lines) {
            // 主行缺主键或品种：无法挂接子表盈亏
            if (line == null || line.getId() == null || line.getItemId() == null) {
                continue;
            }
            adjustmentIds.add(line.getId());
            adjustmentIdToItemId.put(line.getId(), line.getItemId());
        }
        // 无有效主行：结束
        if (adjustmentIds.isEmpty()) {
            return result;
        }
        List<PriceAdjustmentDetail> details =
            this.detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);
        for (PriceAdjustmentDetail detail : details) {
            // 空子表行跳过
            if (detail == null) {
                continue;
            }
            // loc 空：不计入任一库房期初期末明细（历史单/诊疗/未解析库位）
            if (detail.getLocationId() == null) {
                continue;
            }
            // 非本库：跳过
            if (!locationId.equals(detail.getLocationId())) {
                continue;
            }
            // 未算进价盈亏（仅调售价等）：不计入进价账平
            if (detail.getPurchaseImpact() == null) {
                continue;
            }
            Long itemId = adjustmentIdToItemId.get(detail.getAdjustmentId());
            // 子表外键挂不上主行：丢弃，避免脏汇总
            if (itemId == null) {
                continue;
            }
            BigDecimal current = result.get(itemId);
            // 同品种多行累加本库进价盈亏
            if (current == null) {
                result.put(itemId, detail.getPurchaseImpact());
            } else {
                result.put(itemId, current.add(detail.getPurchaseImpact()));
            }
        }
        return result;
    }

    /**
     * 已生效调价项目主行计数（调价明细列表 AS-FIN-PRC-002）。
     *
     * @param query 过滤与分页入参；{@code itemIds} 空列表时直接返回 0
     * @return 命中主行条数
     */
    public long countApprovedLines(PriceAdjustmentApprovedLineListQuery query) {
        if (query != null && query.getItemIds() != null && query.getItemIds().isEmpty()) {
            return 0L;
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<PriceAdjustment> root = cq.from(PriceAdjustment.class);
        cq.select(cb.count(root));
        cq.where(PriceAdjustmentApprovedLineListPredicateSupport.collectPredicates(root, cb, query)
            .toArray(new Predicate[0]));
        Long n = this.entityManager.createQuery(cq).getSingleResult();
        if (n == null) {
            return 0L;
        }
        return n.longValue();
    }

    /**
     * 已生效调价项目主行分页（调价明细列表；不含子表，含行级盈亏合计）。
     *
     * @param query 过滤与分页入参；null 或 {@code itemIds} 空列表时返回空列表
     * @return 当前页主行读模型（{@code details} 为空列表）
     */
    public List<PriceAdjustmentLineQueryModel> pageApprovedLines(PriceAdjustmentApprovedLineListQuery query) {
        if (query == null) {
            return Collections.emptyList();
        }
        if (query.getItemIds() != null && query.getItemIds().isEmpty()) {
            return Collections.emptyList();
        }
        int pageSize = query.getBoundedPageSize();
        if (pageSize < 1) {
            return Collections.emptyList();
        }
        int firstResult = query.springPageIndex() * pageSize;
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<PriceAdjustment> cq = cb.createQuery(PriceAdjustment.class);
        Root<PriceAdjustment> root = cq.from(PriceAdjustment.class);
        cq.where(PriceAdjustmentApprovedLineListPredicateSupport.collectPredicates(root, cb, query)
            .toArray(new Predicate[0]));
        List<jakarta.persistence.criteria.Order> secondary = List.of(
            cb.desc(root.get(FieldNameConstants.APPROVE_TIME)),
            cb.desc(root.get(FieldNameConstants.ID)));
        // B.2.1：单号关键字精确全等置顶，次级生效时间/主键倒序
        if (StringUtils.isNotEmpty(query.getBusNo())) {
            PriceAdjustmentApprovedLineListPredicateSupport.applyBusNoExactFirstOrder(
                cq, root, cb, query, secondary);
        } else {
            cq.orderBy(secondary);
        }
        TypedQuery<PriceAdjustment> tq = this.entityManager.createQuery(cq);
        tq.setFirstResult(firstResult);
        tq.setMaxResults(pageSize);
        List<PriceAdjustment> rows = tq.getResultList();
        return this.toApprovedLineListModels(rows);
    }

    /**
     * 按主行主键加载已生效调价项目行及全部子表明细（调价明细详情）。
     *
     * @param id 主行主键 {@code cus_price_adjustment.id}
     * @return 主行读模型（含子表与行级盈亏合计）
     * @throws BusinessException 主键为空、记录不存在或非已生效状态时
     */
    public PriceAdjustmentLineQueryModel getApprovedLineById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价行主键不能为空");
        }
        PriceAdjustment line = repository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND,
                "价格调整记录不存在"
            ));
        if (!PriceAdjustmentStatus.APPROVED.getCode().equals(line.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND,
                "价格调整记录不存在或尚未生效"
            );
        }
        List<PriceAdjustmentLineQueryModel> models = this.toLineModels(List.of(line));
        return models.get(0);
    }

    /**
     * 按机构与提交时间区间分页列出调价单（不限审批状态）。
     *
     * @param orgId      机构
     * @param startTime  开始时间（含）
     * @param endTime    结束时间（含）
     * @param pageable   分页
     * @return 单据分页
     * @throws BusinessException 机构为空时
     */
    public Page<PriceAdjustmentDocumentQueryModel> listByOrgAndSubmitTime(
        Long orgId,
        Date startTime,
        Date endTime,
        Pageable pageable
    ) {
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "机构不能为空");
        }
        List<PriceAdjustment> rows = repository.findByOrgIdAndSubmitTimeBetweenAndIsDeletedFalseOrderBySubmitTimeDesc(
            orgId,
            startTime,
            endTime
        );
        Map<String, List<PriceAdjustment>> grouped = this.groupByBusNo(rows);
        List<String> busNos = new ArrayList<>(grouped.keySet());
        return this.pageDocuments(busNos, grouped, pageable);
    }

    /**
     * 加载单据主行（未删）。
     *
     * @param busNo 单号
     * @return 主行列表
     * @throws BusinessException 单号为空或不存在时
     */
    private List<PriceAdjustment> requireLines(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        List<PriceAdjustment> lines = repository.findByBusNoAndIsDeletedFalse(busNo.trim());
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND, "价格调整记录不存在");
        }
        return lines;
    }

    /**
     * 按调价单号分组。
     *
     * @param rows 主行
     * @return 单号 → 行列表
     */
    private Map<String, List<PriceAdjustment>> groupByBusNo(List<PriceAdjustment> rows) {
        Map<String, List<PriceAdjustment>> grouped = new LinkedHashMap<>();
        for (PriceAdjustment row : rows) {
            if (row == null || row.getBusNo() == null) {
                continue;
            }
            List<PriceAdjustment> bucket = grouped.get(row.getBusNo());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(row.getBusNo(), bucket);
            }
            bucket.add(row);
        }
        return grouped;
    }

    /**
     * 对单号列表分页并组装单据读模型（内存分页，审批台待办量级可接受）。
     *
     * @param busNos   单号列表
     * @param grouped  分组数据
     * @param pageable 分页
     * @return 分页结果
     */
    private Page<PriceAdjustmentDocumentQueryModel> pageDocuments(
        List<String> busNos,
        Map<String, List<PriceAdjustment>> grouped,
        Pageable pageable
    ) {
        int total = busNos.size();
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int fromIndex = pageNumber * pageSize;
        if (fromIndex >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<PriceAdjustmentDocumentQueryModel> content = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index++) {
            String busNo = busNos.get(index);
            content.add(this.toDocumentModel(busNo, grouped.get(busNo)));
        }
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 组装单据读模型。
     *
     * @param busNo 单号
     * @param lines 主行
     * @return 读模型
     */
    private PriceAdjustmentDocumentQueryModel toDocumentModel(String busNo, List<PriceAdjustment> lines) {
        PriceAdjustmentDocumentQueryModel document = new PriceAdjustmentDocumentQueryModel();
        document.setBusNo(busNo);
        if (lines == null || lines.isEmpty()) {
            document.setLines(List.of());
            return document;
        }
        PriceAdjustment anchor = lines.get(0);
        document.setStatusEnum(anchor.getStatusEnum());
        document.setSubmitterId(anchor.getSubmitterId());
        document.setSubmitTime(anchor.getSubmitTime());
        document.setApproverId(anchor.getApproverId());
        document.setApproveTime(anchor.getApproveTime());
        document.setApproveComment(anchor.getApproveComment());
        document.setLines(this.toLineModels(lines));
        return document;
    }

    /**
     * 主行列表转读模型（批量 IN 加载子表，禁止按行循环仓储）。
     *
     * @param lines 主行
     * @return 行读模型
     */
    private List<PriceAdjustmentLineQueryModel> toLineModels(List<PriceAdjustment> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Long> adjustmentIds = new ArrayList<>();
        for (PriceAdjustment line : lines) {
            adjustmentIds.add(line.getId());
        }
        // 一次批量加载全部子表，再按 adjustment_id 分组挂接
        List<PriceAdjustmentDetail> allDetails = detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);
        Map<Long, List<PriceAdjustmentDetail>> detailsByAdjustmentId = new HashMap<>();
        for (PriceAdjustmentDetail detail : allDetails) {
            List<PriceAdjustmentDetail> bucket = detailsByAdjustmentId.get(detail.getAdjustmentId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                detailsByAdjustmentId.put(detail.getAdjustmentId(), bucket);
            }
            bucket.add(detail);
        }

        List<PriceAdjustmentLineQueryModel> models = new ArrayList<>();
        for (PriceAdjustment line : lines) {
            PriceAdjustmentLineQueryModel model = this.mapLineHeader(line);
            List<PriceAdjustmentDetail> details = detailsByAdjustmentId.get(line.getId());
            List<PriceAdjustmentDetailQueryModel> detailModels = new ArrayList<>();
            BigDecimal totalImpact = BigDecimal.ZERO;
            if (details != null) {
                for (PriceAdjustmentDetail detail : details) {
                    PriceAdjustmentDetailQueryModel detailModel = new PriceAdjustmentDetailQueryModel();
                    detailModel.setId(detail.getId());
                    detailModel.setLocationId(detail.getLocationId());
                    detailModel.setLotNumber(detail.getLotNumber());
                    detailModel.setQuantity(detail.getQuantity());
                    detailModel.setOldPurchaseAmount(detail.getOldPurchaseAmount());
                    detailModel.setOldSellingAmount(detail.getOldSellingAmount());
                    detailModel.setPurchaseImpact(detail.getPurchaseImpact());
                    detailModel.setSellingImpact(detail.getSellingImpact());
                    detailModels.add(detailModel);
                    if (detail.getPurchaseImpact() != null) {
                        totalImpact = totalImpact.add(detail.getPurchaseImpact());
                    }
                    if (detail.getSellingImpact() != null) {
                        totalImpact = totalImpact.add(detail.getSellingImpact());
                    }
                }
            }
            model.setDetails(detailModels);
            model.setTotalImpact(totalImpact);
            models.add(model);
        }
        return models;
    }

    /**
     * 已生效主行列表读模型（不含子表明细，批量汇总行级盈亏）。
     *
     * @param lines 主行实体
     * @return 读模型列表
     */
    private List<PriceAdjustmentLineQueryModel> toApprovedLineListModels(List<PriceAdjustment> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Long> adjustmentIds = new ArrayList<>();
        for (PriceAdjustment line : lines) {
            adjustmentIds.add(line.getId());
        }
        Map<Long, BigDecimal> totalImpactById = this.sumTotalImpactByAdjustmentIds(adjustmentIds);
        List<PriceAdjustmentLineQueryModel> models = new ArrayList<>();
        for (PriceAdjustment line : lines) {
            PriceAdjustmentLineQueryModel model = this.mapLineHeader(line);
            model.setDetails(List.of());
            BigDecimal totalImpact = totalImpactById.get(line.getId());
            if (totalImpact == null) {
                totalImpact = BigDecimal.ZERO;
            }
            model.setTotalImpact(totalImpact);
            models.add(model);
        }
        return models;
    }

    /**
     * 主行实体映射为读模型头（不含子表）。
     *
     * @param line 主行
     * @return 头字段读模型
     */
    private PriceAdjustmentLineQueryModel mapLineHeader(PriceAdjustment line) {
        PriceAdjustmentLineQueryModel model = new PriceAdjustmentLineQueryModel();
        model.setId(line.getId());
        model.setBusNo(line.getBusNo());
        model.setItemTable(line.getItemTable());
        model.setItemId(line.getItemId());
        model.setNewPurchaseAmount(line.getNewPurchaseAmount());
        model.setNewSellingAmount(line.getNewSellingAmount());
        model.setAdjustmentReason(line.getAdjustmentReason());
        model.setAdjustmentTime(line.getAdjustmentTime());
        model.setStatusEnum(line.getStatusEnum());
        model.setOperatorId(line.getOperatorId());
        model.setOrgId(line.getOrgId());
        model.setApproverId(line.getApproverId());
        model.setApproveTime(line.getApproveTime());
        model.setAdjustmentStyle(PriceAdjustmentItemTableSupport.resolveAdjustmentStyle(line.getItemTable()));
        return model;
    }

    /**
     * 批量汇总子表盈亏为行级合计（进价盈亏 + 售价盈亏）。
     *
     * @param adjustmentIds 主行主键集合
     * @return 主行 id → 盈亏合计
     */
    private Map<Long, BigDecimal> sumTotalImpactByAdjustmentIds(List<Long> adjustmentIds) {
        if (adjustmentIds == null || adjustmentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PriceAdjustmentDetail> allDetails =
            detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);
        Map<Long, BigDecimal> result = new HashMap<>();
        for (PriceAdjustmentDetail detail : allDetails) {
            BigDecimal lineTotal = result.get(detail.getAdjustmentId());
            if (lineTotal == null) {
                lineTotal = BigDecimal.ZERO;
            }
            if (detail.getPurchaseImpact() != null) {
                lineTotal = lineTotal.add(detail.getPurchaseImpact());
            }
            if (detail.getSellingImpact() != null) {
                lineTotal = lineTotal.add(detail.getSellingImpact());
            }
            result.put(detail.getAdjustmentId(), lineTotal);
        }
        return result;
    }
}
