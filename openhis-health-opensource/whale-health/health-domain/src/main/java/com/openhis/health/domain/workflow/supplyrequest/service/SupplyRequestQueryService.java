/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.service;

import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.query.DeptIssueReportPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoBatchQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByOriginalBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestIdListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportSummaryRow;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.repository.SupplyRequestRepository;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestSettlementWindowQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestSettlementLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyRequestDocumentListPredicateSupport;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyRequestSettlementWindowPredicateSupport;
import com.openhis.health.domain.workflow.supplyrequest.support.WarehouseDocumentDetailPagePredicateSupport;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 供应请求读侧查询服务。
 *
 * <p>职责：按业务单号、原单号、主键批量查询供应申请，并提供审批态门控与行编辑可行性判断；只读不写库。</p>
 *
 * <p>另提供 {@code wkf_supply_request} 上「按 {@code bus_no} 聚合」的通用单据列表能力（{@link #countDistinctBusNo}、{@link #listDistinctBusNoPage}、
 * {@link #listByBusNosWithFilter}），进货/订货/退货等各 AS 通过 {@link com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery}
 * 收窄 {@code type_enum} 与位置等条件后复用；库房期初期末明细期间动账见 {@link #listAgreedLinesByWarehouseAndApprovalWindow}。</p>
 *
 * <p>读模型 {@link com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel} 中 {@code typeEnum}/{@code statusEnum} 等字段
 * 与实体列一致，均为持久化 {@code getCode()} 字符串（B.10.8.1）；本服务内与 {@link com.openhis.health.domain.share.enums.SupplyStatus} 比对时使用
 * {@code SupplyStatus.XXX.getCode()}。</p>
 * <p>聚合边界：仅本聚合 {@link SupplyRequestRepository}；{@link EntityManager} 仅用于 {@code COUNT(DISTINCT bus_no)} 等派生无法表达的只读查询。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplyRequestQueryService {

    private final SupplyRequestRepository supplyRequestRepository;

    /**
     * 供应请求持久化上下文：用于 {@code COUNT(DISTINCT bus_no)}、{@code GROUP BY bus_no} 且按组内 {@code apply_time} 聚合排序等
     * 无法仅靠 {@link org.springframework.data.jpa.repository.JpaRepository} 派生方法表达的只读查询（B.2 领域层 EM 例外说明）。
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 按业务单号精确查询供应请求列表。
     *
     * @param query 含 busNo
     * @return 模型列表；入参无效时空列表
     */
    public List<SupplyRequestModel> getSupplyByBusNo(SupplyByBusNoQuery query) {
        if (query == null || query.getBusNo() == null || query.getBusNo().trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(query.getBusNo().trim());
        return this.toModels(entities);
    }

    /**
     * 按多个业务单号批量查询。
     *
     * @param query 含 busNo 列表
     * @return 模型列表
     */
    public List<SupplyRequestModel> getSupplyByBusNoBatch(SupplyByBusNoBatchQuery query) {
        if (query == null || query.getBusNoList() == null || query.getBusNoList().isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoInAndIsDeletedFalse(query.getBusNoList());
        return this.toModels(entities);
    }

    /**
     * 按原始业务单号（如退费关联原单）查询供应请求。
     *
     * @param query 含 originalBusNo
     * @return 模型列表
     */
    public List<SupplyRequestModel> getSupplyByOriginalBusNo(SupplyByOriginalBusNoQuery query) {
        if (query == null || query.getOriginalBusNo() == null || query.getOriginalBusNo().trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyRequest> entities = supplyRequestRepository.findByOriginalBusNo(query.getOriginalBusNo().trim());
        return this.toModels(entities);
    }

    /**
     * 判断是否存在未软删的指定类型供应单以 {@code original_bus_no} 引用来源单号（不区分审批状态；已软删行由仓储过滤器排除）。
     *
     * @param originalBusNo 来源单号（如订货 {@code bus_no}）
     * @param typeEnum      下游单据 {@code type_enum} 持久化 code
     * @param excludeBusNo  改单时排除的下游 {@code bus_no}，可 blank
     * @return 已被其它未删除下游单引用时 {@code true}
     */
    public boolean existsUndeletedSupplyByOriginalBusNo(
        String originalBusNo,
        String typeEnum,
        String excludeBusNo) {
        if (originalBusNo == null || originalBusNo.trim().isEmpty()) {
            return false;
        }
        if (typeEnum == null || typeEnum.trim().isEmpty()) {
            return false;
        }
        String trimmedOriginal = originalBusNo.trim();
        String trimmedType = typeEnum.trim();
        if (excludeBusNo != null && !excludeBusNo.trim().isEmpty()) {
            return supplyRequestRepository.existsByOriginalBusNoAndTypeEnumAndBusNoNot(
                trimmedOriginal, trimmedType, excludeBusNo.trim());
        }
        return supplyRequestRepository.existsByOriginalBusNoAndTypeEnum(trimmedOriginal, trimmedType);
    }

    /**
     * 按主键集合批量加载。
     *
     * @param query 含 id 列表
     * @return 模型列表
     */
    public List<SupplyRequestModel> selectByIdList(SupplyRequestIdListQuery query) {
        if (query == null || query.getIdList() == null || query.getIdList().isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyRequest> entities = supplyRequestRepository.findByIdIn(query.getIdList());
        return this.toModels(entities);
    }

    /**
     * 按主键加载单条供应请求简模。
     *
     * @param id 主键
     * @return 模型
     * @throws BusinessException 参数非法或记录不存在
     */
    public SupplyRequestModel getById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "id 不能为空");
        }
        SupplyRequest entity = supplyRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "供应请求不存在"));
        return this.mapEntityToModel(entity);
    }

    /**
     * 判断该业务单号下是否已存在「已同意」态记录（{@code status_enum} 等于 {@link com.openhis.health.domain.share.enums.SupplyStatus#AGREE} 的持久化 {@code getCode()}），用于关单或重复同意门控。
     *
     * @param busNo 业务单号
     * @return 校验结果（含 {@code hasAgreed} 布尔）
     * @throws BusinessException {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} {@code busNo} 为空
     */
    public SupplyRequestValidationModel supplyRequestValidation(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        boolean hasAgreed = supplyRequestRepository.existsByBusNoAndStatusEnum(busNo.trim(), SupplyStatus.AGREE.getCode());
        SupplyRequestValidationModel model = new SupplyRequestValidationModel();
        model.setBusNo(busNo.trim());
        model.setHasAgreed(hasAgreed);
        return model;
    }

    /**
     * 校验指定 {@code busNo} + {@code itemId} 行是否仍允许编辑：若该行（或同号同行）处于审核中/已同意（{@link com.openhis.health.domain.share.enums.SupplyStatus#APPROVAL} /
     * {@link com.openhis.health.domain.share.enums.SupplyStatus#AGREE} 的持久化 code）则不可改。
     *
     * @param query 含 {@code busNo} 与 {@code itemId}
     * @return {@code true} 表示可编辑；{@code false} 表示存在阻塞状态
     * @throws BusinessException 入参不完整
     */
    public Boolean verifyAbleEdit(VerifyAbleEditQuery query) {
        if (query == null || query.getBusNo() == null || query.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        if (query.getItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "itemId 不能为空");
        }
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(query.getBusNo().trim());
        // Loop：遍历单号下全部行，逐条校验是否允许编辑
        for (SupplyRequest entity : entities) {
            if (entity == null) {
                continue;
            }
            if (entity.getItemId() == null) {
                continue;
            }
            if (!entity.getItemId().equals(query.getItemId())) {
                continue;
            }
            if (SupplyStatus.APPROVAL.getCode().equals(entity.getStatusEnum()) || SupplyStatus.AGREE.getCode().equals(entity.getStatusEnum())) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    /**
     * 判断指定目录物品是否已有审核中或已同意的供应入库事实（三大目录库存侧锁定门控）。
     *
     * @param itemId 物品主键 {@code wkf_supply_request.item_id}
     * @return 存在时为 true
     */
    public boolean existsInboundByItemId(Long itemId) {
        if (itemId == null) {
            return false;
        }
        List<String> blockingStatuses = new ArrayList<>();
        blockingStatuses.add(SupplyStatus.APPROVAL.getCode());
        blockingStatuses.add(SupplyStatus.AGREE.getCode());
        return supplyRequestRepository.existsByItemIdAndStatusEnumInAndIsDeletedFalse(itemId, blockingStatuses);
    }

    /**
     * 取该业务单号下首行的物品主表名（多行时取列表首条）。
     *
     * @param busNo 业务单号
     * @return 主表名；无记录时为 null
     * @throws BusinessException busNo 为空
     */
    public String getItemTable(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo.trim());
        if (entities.isEmpty()) {
            return null;
        }
        SupplyRequest first = entities.get(0);
        return first.getItemTable();
    }

    /**
     * 汇总某业务单号涉及的去重物品主键列表。
     *
     * @param busNo 业务单号
     * @return 物品 id 列表
     * @throws BusinessException busNo 为空
     */
    public List<Long> getItem(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo.trim());
        Set<Long> itemIdSet = new HashSet<>();
        // Loop：遍历单号下全部行，汇总去重物品标识
        for (SupplyRequest entity : entities) {
            if (entity == null) {
                continue;
            }
            if (entity.getItemId() == null) {
                continue;
            }
            itemIdSet.add(entity.getItemId());
        }
        return new ArrayList<>(itemIdSet);
    }

    /**
     * 各类供应单据列表：在 {@code wkf_supply_request} 上统计不重复 {@code bus_no}（与 {@link #listDistinctBusNoPage} 过滤一致）。
     *
     * @param query 业务收窄条件；允许为 null（仅保留软删等基线谓词）
     * @return 去重后的单据条数
     */
    public long countDistinctBusNo(SupplyRequestDocumentListQuery query) {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<SupplyRequest> root = cq.from(SupplyRequest.class);
        // 统计口径为「业务单号去重后的单据条数」，与后续 GROUP BY 分页列表对齐。
        cq.select(cb.countDistinct(root.get(FieldNameConstants.BUS_NO)));
        cq.where(SupplyRequestDocumentListPredicateSupport.collectPredicates(root, cb, query).toArray(new Predicate[0]));
        Long n = this.entityManager.createQuery(cq).getSingleResult();
        if (n == null) {
            return 0L;
        }
        return n.longValue();
    }

    /**
     * 各类供应单据列表：按 {@code bus_no} 分组分页（组内最大 {@code apply_time} 降序）。
     * 分页下标与页大小取自 {@link SupplyRequestDocumentListQuery#getBoundedPageSize()} 与 {@link com.openhis.health.domain.share.query.AbstractPageQuery#springPageIndex()}（B.10.11）。
     *
     * @param query 业务收窄与分页入参；为 null 时返回空列表
     * @return 当前页业务单号列表（顺序与排序一致）
     */
    public List<String> listDistinctBusNoPage(SupplyRequestDocumentListQuery query) {
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
        Root<SupplyRequest> root = cq.from(SupplyRequest.class);
        cq.select(root.get(FieldNameConstants.BUS_NO));
        cq.where(SupplyRequestDocumentListPredicateSupport.collectPredicates(root, cb, query).toArray(new Predicate[0]));
        // 同号多行共享审批头：列表按单号聚合，组内以最新申请时刻作为组排序键。
        cq.groupBy(root.get(FieldNameConstants.BUS_NO));
        cq.orderBy(cb.desc(cb.max(root.get(FieldNameConstants.APPLY_TIME))));
        TypedQuery<String> tq = this.entityManager.createQuery(cq);
        // OFFSET/LIMIT 下推到数据库，禁止内存 subList 伪分页（B.2）。
        tq.setFirstResult(firstResult);
        tq.setMaxResults(pageSize);
        return tq.getResultList();
    }

    /**
     * 各类供应单据列表：在相同过滤条件下加载给定单号集合下的全部明细行（供按单聚合装配）。
     *
     * @param filter 与列表统计/分页相同的收窄条件；允许为 null
     * @param busNos 当前页业务单号集合；空或 null 时返回空列表
     * @return 明细行读模型
     */
    public List<SupplyRequestModel> listByBusNosWithFilter(SupplyRequestDocumentListQuery filter, List<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        // 单号集合由上一阶段分页查询给出，此处用 IN 一次性拉回当前页全部明细行，避免按单号循环查库（A.9.1）。
        Specification<SupplyRequest> spec = (root, cq, cb) -> {
            List<Predicate> predicates = SupplyRequestDocumentListPredicateSupport.collectPredicates(root, cb, filter);
            predicates.add(root.get(FieldNameConstants.BUS_NO).in(busNos));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<SupplyRequest> rows = this.supplyRequestRepository.findAll(spec, Sort.by(Sort.Order.desc(FieldNameConstants.APPLY_TIME)));
        return this.toModels(rows);
    }

    /**
     * 库房单据明细分页（替代 {@code WarehouseDocumentDetailRMQueryService#pageWarehouseDocumentDetail}；查 {@code wkf_supply_request} 明细行）。
     *
     * <p>默认按审批时间倒序；有主 keyword 时 Spec 内精确优先 + 审批时间倒序。</p>
     */
    public Page<WarehouseDocumentDetailQueryModel> pageWarehouseDocumentDetail(WarehouseDocumentDetailPageQuery query) {
        int springIndex = 0;
        int pageSize = 10;
        if (query != null) {
            springIndex = query.springPageIndex();
            pageSize = query.getBoundedPageSize();
        }
        // 有关键字时无 Sort，保留 Spec 内「精确优先 + approvalTime」ORDER BY（B.2.1）
        Pageable pageable;
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())) {
            pageable = PageRequest.of(springIndex, pageSize);
        } else {
            pageable = PageRequest.of(springIndex, pageSize,
                Sort.by(Sort.Order.desc(FieldNameConstants.APPROVAL_TIME)));
        }
        Specification<SupplyRequest> specification = (root, criteriaQuery, cb) -> cb.and(
            WarehouseDocumentDetailPagePredicateSupport.collectPagePredicates(root, criteriaQuery, cb, query)
                .toArray(new Predicate[0]));
        return this.supplyRequestRepository.findAll(specification, pageable).map(this::mapToWarehouseDocumentDetailQueryModel);
    }

    /**
     * 实体列表映射为读模型列表。
     *
     * @param entities 实体集合
     * @return 模型列表
     */
    /**
     * 将供应请求实体列表映射为读模型列表，跳过 null 元素。
     *
     * @param entities 实体集合，允许含 null
     * @return 模型列表；入参为空时返回空列表
     */
    private List<SupplyRequestModel> toModels(List<SupplyRequest> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyRequestModel> models = new ArrayList<>();
        // Loop：遍历实体集合，逐条映射为读模型
        for (SupplyRequest entity : entities) {
            if (entity == null) {
                continue;
            }
            models.add(this.mapEntityToModel(entity));
        }
        return models;
    }

    /**
     * 将聚合行映射为读模型（订货详情等多行场景逐行携带完整字段）。
     *
     * @param entity 持久化实体
     * @return 读模型
     */
    private SupplyRequestModel mapEntityToModel(SupplyRequest entity) {
        SupplyRequestModel model = new SupplyRequestModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setTypeEnum(entity.getTypeEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setCategoryEnum(entity.getCategoryEnum());
        model.setSourceTypeEnum(entity.getSourceTypeEnum());
        model.setSourceLocationId(entity.getSourceLocationId());
        model.setPurposeTypeEnum(entity.getPurposeTypeEnum());
        model.setPurposeLocationId(entity.getPurposeLocationId());
        model.setConsumingOrganizationId(entity.getConsumingOrganizationId());
        model.setSupplierId(entity.getSupplierId());
        model.setItemTable(entity.getItemTable());
        model.setItemId(entity.getItemId());
        model.setItemQuantity(entity.getItemQuantity());
        model.setTotalQuantity(entity.getTotalQuantity());
        model.setUnitCode(entity.getUnitCode());
        model.setOccurrenceTime(entity.getOccurrenceTime());
        model.setPractitionerId(entity.getPractitionerId());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setApplyTime(entity.getApplyTime());
        model.setApprovalTime(entity.getApprovalTime());
        model.setApplicantId(entity.getApplicantId());
        model.setApproverId(entity.getApproverId());
        model.setPrice(entity.getPrice());
        model.setTotalPrice(entity.getTotalPrice());
        model.setProfitLossPrice(entity.getProfitLossPrice());
        model.setReason(entity.getReason());
        model.setLotNumber(entity.getLotNumber());
        model.setTraceNo(entity.getTraceNo());
        model.setRemake(entity.getRemake());
        model.setInvoiceNo(entity.getInvoiceNo());
        model.setOriginalBusNo(entity.getOriginalBusNo());
        model.setSummaryId(entity.getSummaryId());
        model.setPatientId(entity.getPatientId());
        model.setVersion(entity.getVersion());
        return model;
    }

    /**
     * SupplyRequest 实体 → 药库单据详情读模型（头+行公共字段）。
     *
     * @param entity 供应请求实体
     * @return 详情读模型
     */
    private WarehouseDocumentDetailQueryModel mapToWarehouseDocumentDetailQueryModel(SupplyRequest entity) {
        WarehouseDocumentDetailQueryModel model = new WarehouseDocumentDetailQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setOriginalBusNo(entity.getOriginalBusNo());
        model.setTypeEnum(entity.getTypeEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setCategoryEnum(entity.getCategoryEnum());
        model.setSummaryId(entity.getSummaryId());
        model.setDeliverId(entity.getDeliverId());
        model.setSourceTypeEnum(entity.getSourceTypeEnum());
        model.setSourceLocationId(entity.getSourceLocationId());
        model.setPurposeTypeEnum(entity.getPurposeTypeEnum());
        model.setPurposeLocationId(entity.getPurposeLocationId());
        // 领用/退领：落库领用科室带出，供单据详情报表回显
        model.setConsumingOrganizationId(entity.getConsumingOrganizationId());
        model.setItemTable(entity.getItemTable());
        model.setItemId(entity.getItemId());
        model.setItemQuantity(entity.getItemQuantity());
        model.setTotalQuantity(entity.getTotalQuantity());
        model.setUnitCode(entity.getUnitCode());
        model.setLotNumber(entity.getLotNumber());
        model.setTraceNo(entity.getTraceNo());
        model.setPrice(entity.getPrice());
        model.setTotalPrice(entity.getTotalPrice());
        model.setProfitLossPrice(entity.getProfitLossPrice());
        model.setSupplierId(entity.getSupplierId());
        model.setApplicantId(entity.getApplicantId());
        model.setApplyTime(entity.getApplyTime());
        model.setApproverId(entity.getApproverId());
        model.setApprovalTime(entity.getApprovalTime());
        model.setPractitionerId(entity.getPractitionerId());
        model.setOccurrenceTime(entity.getOccurrenceTime());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setReason(entity.getReason());
        model.setRemake(entity.getRemake());
        return model;
    }

    /**
     * 住院护士站汇总领药 · 汇总单合并明细行（**AS-IP-NUR-010 · IF-IP-SUM-03**）。
     *
     * <p>以 SupplyRequest 合并行为准；单价/总价由应用层 {@code InpatientMedicationDispenseChargePricingLoader} 协作回填（**BR-IP-SUM-009**）；不返回批号（**BR-IP-SUM-010**）。</p>
     *
     * @param summaryNo 汇总单号 / busNo
     * @return 合并明细行；单号无效或无 type 27 行时返回空列表
     */
    public List<MedicationSummarySlipLineQueryModel> listMedicationSummarySlipLinesByBusNo(String summaryNo) {
        if (summaryNo == null || summaryNo.isBlank()) {
            return Collections.emptyList();
        }
        String busNo = summaryNo.trim();
        SupplyByBusNoQuery busNoQuery = new SupplyByBusNoQuery();
        busNoQuery.setBusNo(busNo);
        List<SupplyRequestModel> supplyLines = this.getSupplyByBusNo(busNoQuery);
        if (supplyLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyRequestModel> summaryLines = new ArrayList<>();
        for (SupplyRequestModel line : supplyLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.MEDICATION_SUMMARY_ORDER.getCode().equals(line.getTypeEnum())) {
                continue;
            }
            summaryLines.add(line);
        }
        if (summaryLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<MedicationSummarySlipLineQueryModel> result = new ArrayList<>();
        for (SupplyRequestModel line : summaryLines) {
            MedicationSummarySlipLineQueryModel model = new MedicationSummarySlipLineQueryModel();
            model.setSupplyRequestId(line.getId());
            model.setBusNo(line.getBusNo());
            model.setItemId(line.getItemId());
            model.setItemQuantity(line.getItemQuantity());
            model.setUnitCode(line.getUnitCode());
            result.add(model);
        }
        return result;
    }

    /**
     * 库房期初期末明细期间供应动账批量读（**AS-INV-RPT-001** · **DM-WKF-006 §6.2.12**）。
     *
     * <p>仅返回 {@link SupplyStatus#AGREE} 明细行；源/目的 ± 与品种汇总由应用层完成。</p>
     *
     * @param query 库房、审批时刻窗、{@code item_table} 与类型白名单；关键入参缺失时返回空列表
     * @return 供应明细行读模型；允许空列表
     */
    public List<SupplyRequestSettlementLineQueryModel> listAgreedLinesByWarehouseAndApprovalWindow(
        SupplyRequestSettlementWindowQuery query) {
        if (query == null || query.getWarehouseId() == null) {
            return Collections.emptyList();
        }
        if (StringUtils.isEmpty(query.getItemTable())) {
            return Collections.emptyList();
        }
        if (query.getTypeEnumWhitelist() == null || query.getTypeEnumWhitelist().isEmpty()) {
            return Collections.emptyList();
        }
        Specification<SupplyRequest> specification = (root, criteriaQuery, cb) -> cb.and(
            SupplyRequestSettlementWindowPredicateSupport.collectPredicates(root, cb, query)
                .toArray(new Predicate[0]));
        List<SupplyRequest> rows = this.supplyRequestRepository.findAll(
            specification, Sort.by(Sort.Order.asc(FieldNameConstants.APPROVAL_TIME), Sort.Order.asc(FieldNameConstants.ID)));
        return this.toSettlementLineModels(rows);
    }

    /**
     * 供应实体列表映射为库房期初期末明细行读模型。
     *
     * @param entities 持久化行
     * @return 日结行列表
     */
    private List<SupplyRequestSettlementLineQueryModel> toSettlementLineModels(List<SupplyRequest> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyRequestSettlementLineQueryModel> models = new ArrayList<>();
        for (SupplyRequest entity : entities) {
            if (entity == null) {
                continue;
            }
            models.add(this.mapEntityToSettlementLineModel(entity));
        }
        return models;
    }

    /**
     * 单条供应行映射为日结读模型（仅日结所需字段）。
     *
     * @param entity 供应请求实体
     * @return 日结行读模型
     */
    private SupplyRequestSettlementLineQueryModel mapEntityToSettlementLineModel(SupplyRequest entity) {
        SupplyRequestSettlementLineQueryModel model = new SupplyRequestSettlementLineQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setTypeEnum(entity.getTypeEnum());
        model.setSourceLocationId(entity.getSourceLocationId());
        model.setPurposeLocationId(entity.getPurposeLocationId());
        model.setItemTable(entity.getItemTable());
        model.setItemId(entity.getItemId());
        model.setItemQuantity(entity.getItemQuantity());
        model.setPrice(entity.getPrice());
        model.setTotalPrice(entity.getTotalPrice());
        model.setProfitLossPrice(entity.getProfitLossPrice());
        model.setLotNumber(entity.getLotNumber());
        model.setApprovalTime(entity.getApprovalTime());
        return model;
    }

    /**
     * 科室领用/退领报表明细分页（AS-RPT-003～006 科室侧；DM-WKF-006 §6.2.15）。
     *
     * <p>仅已同意 {@code DEPT_ISSUE}/{@code DEPT_ISSUE_RETURN}；按 {@code item_table} 收窄药品或耗材；
     * 业务时间优先 {@code approval_time}。仅本聚合 Repository。
     * 门诊/住院域库位由应用层预写 {@code resolvedPurposeLocationIds}。</p>
     *
     * @param query 分页与筛选；{@code itemTable} 为空时返回空页
     * @return 明细分页；业务时间倒序
     */
    public Page<DeptIssueReportLineQueryModel> pageDeptIssueReportLines(DeptIssueReportPageQuery query) {
        if (query == null) {
            query = new DeptIssueReportPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        // Gate：缺品规表拒查，避免药品/耗材混页
        if (StringUtils.isEmpty(query.getItemTable())) {
            Pageable emptyPageable = PageRequest.of(pageNo, pageSize);
            return new PageImpl<>(Collections.emptyList(), emptyPageable, 0L);
        }
        // Pre：规约 + 仓储分页
        Specification<SupplyRequest> specification = this.buildDeptIssueReportSpecification(query);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.APPROVAL_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<SupplyRequest> page = supplyRequestRepository.findAll(specification, pageable);
        // Asm：行字段直映；科室组织名由应用层 enrich
        List<DeptIssueReportLineQueryModel> models = new ArrayList<>();
        for (SupplyRequest row : page.getContent()) {
            models.add(this.toDeptIssueReportLineModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 科室领用/退领报表全量列表（导出；行数受 {@link DeptIssueReportPageQuery#getBoundedPageSize()} 约束）。
     *
     * @param query 与分页同筛选
     * @return 明细行列表
     */
    public List<DeptIssueReportLineQueryModel> listDeptIssueReportLines(DeptIssueReportPageQuery query) {
        if (query == null) {
            query = new DeptIssueReportPageQuery();
        }
        // Gate：缺品规表拒查
        if (StringUtils.isEmpty(query.getItemTable())) {
            return Collections.emptyList();
        }
        int maxRows = query.getBoundedPageSize();
        Specification<SupplyRequest> specification = this.buildDeptIssueReportSpecification(query);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.APPROVAL_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(0, maxRows, sort);
        Page<SupplyRequest> page = supplyRequestRepository.findAll(specification, pageable);
        List<DeptIssueReportLineQueryModel> models = new ArrayList<>();
        for (SupplyRequest row : page.getContent()) {
            models.add(this.toDeptIssueReportLineModel(row));
        }
        return models;
    }

    /**
     * 科室领用/退领净汇总（按科室/药房/品规；DM-WKF-006 §6.2.15）。
     *
     * @param query 筛选 + {@code summaryDimension}；DEPARTMENT 维须预解析组织映射
     * @return 净汇总行
     */
    public List<DeptIssueReportSummaryRow> summarizeDeptIssueReport(DeptIssueReportPageQuery query) {
        if (query == null) {
            query = new DeptIssueReportPageQuery();
        }
        // Gate：缺品规表拒查
        if (StringUtils.isEmpty(query.getItemTable())) {
            return Collections.emptyList();
        }
        // Pre：全量命中后按维分桶
        Specification<SupplyRequest> specification = this.buildDeptIssueReportSpecification(query);
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.APPROVAL_TIME));
        List<SupplyRequest> rows = supplyRequestRepository.findAll(specification, sort);
        String dimension = StringUtils.trimToNull(query.getSummaryDimension());
        if (dimension == null) {
            dimension = DeptIssueReportConstants.SUMMARY_DIMENSION_DEPARTMENT;
        }
        Map<Long, Long> orgByLocation = query.getOrganizationIdByPurposeLocationId();
        if (orgByLocation == null) {
            orgByLocation = Map.of();
        }
        // Asm：退领数量取负
        Map<String, DeptIssueReportSummaryRow> bucketByKey = new LinkedHashMap<>();
        for (SupplyRequest row : rows) {
            if (row == null) {
                continue;
            }
            String bucketKey = this.resolveDeptIssueReportBucketKey(row, dimension, orgByLocation);
            DeptIssueReportSummaryRow summary = bucketByKey.get(bucketKey);
            if (summary == null) {
                summary = new DeptIssueReportSummaryRow();
                summary.setDimensionKey(bucketKey);
                summary.setSummaryDimension(dimension);
                summary.setNetQuantity(BigDecimal.ZERO);
                summary.setLineCount(0L);
                this.fillDeptIssueReportSummaryIds(summary, row, dimension, orgByLocation);
                bucketByKey.put(bucketKey, summary);
            }
            BigDecimal signedQty = this.resolveDeptIssueReportSignedQuantity(row);
            summary.setNetQuantity(summary.getNetQuantity().add(signedQty));
            summary.setLineCount(summary.getLineCount() + 1L);
        }
        return new ArrayList<>(bucketByKey.values());
    }

    /**
     * 组装科室领用/退领报表规约。
     *
     * @param query 筛选入参
     * @return JPA Specification
     */
    private Specification<SupplyRequest> buildDeptIssueReportSpecification(DeptIssueReportPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), SupplyStatus.AGREE.getCode()));
            predicates.add(root.get(FieldNameConstants.TYPE_ENUM).in(
                SupplyType.DEPT_ISSUE.getCode(),
                SupplyType.DEPT_ISSUE_RETURN.getCode()));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable()));
            if (query.getPurposeLocationId() != null) {
                predicates.add(cb.equal(
                    root.get(FieldNameConstants.PURPOSE_LOCATION_ID),
                    query.getPurposeLocationId()));
            } else {
                this.applyResolvedPurposeLocationIds(
                    predicates, root, cb, query.getResolvedPurposeLocationIds());
            }
            this.applyResolvedItemIds(predicates, root, cb, query.getResolvedItemIds());
            this.applyDeptIssueBusinessTimeWindow(predicates, root, cb, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 预解析科室药房主键 IN；空列表拒查。
     */
    private void applyResolvedPurposeLocationIds(
        List<Predicate> predicates,
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        List<Long> resolvedPurposeLocationIds) {
        if (resolvedPurposeLocationIds == null) {
            return;
        }
        if (resolvedPurposeLocationIds.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.PURPOSE_LOCATION_ID).in(resolvedPurposeLocationIds));
    }

    /**
     * 预解析品规主键 IN；空列表拒查。
     */
    private void applyResolvedItemIds(
        List<Predicate> predicates,
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        List<Long> resolvedItemIds) {
        if (resolvedItemIds == null) {
            return;
        }
        if (resolvedItemIds.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.ITEM_ID).in(resolvedItemIds));
    }

    /**
     * 业务时间窗：优先审批时间，缺失时不入窗（与发药报表空时间口径一致）。
     */
    private void applyDeptIssueBusinessTimeWindow(
        List<Predicate> predicates,
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        DeptIssueReportPageQuery query) {
        if (query.getBusinessTimeFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME),
                query.getBusinessTimeFrom()));
        }
        if (query.getBusinessTimeTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get(FieldNameConstants.APPROVAL_TIME),
                query.getBusinessTimeTo()));
        }
        // 有时间窗时排除审批时间为空的历史脏行
        if (query.getBusinessTimeFrom() != null || query.getBusinessTimeTo() != null) {
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.APPROVAL_TIME)));
        }
    }

    /**
     * 实体 → 领用报表明细读模型。
     */
    private DeptIssueReportLineQueryModel toDeptIssueReportLineModel(SupplyRequest row) {
        DeptIssueReportLineQueryModel model = new DeptIssueReportLineQueryModel();
        model.setSupplyRequestId(row.getId());
        model.setBusNo(row.getBusNo());
        model.setTypeEnum(row.getTypeEnum());
        model.setStatusEnum(row.getStatusEnum());
        model.setItemTable(row.getItemTable());
        model.setItemId(row.getItemId());
        model.setItemQuantity(row.getItemQuantity());
        model.setUnitCode(row.getUnitCode());
        model.setPurposeLocationId(row.getPurposeLocationId());
        model.setConsumingOrganizationId(row.getConsumingOrganizationId());
        model.setLotNumber(row.getLotNumber());
        model.setPrice(row.getPrice());
        model.setTotalPrice(row.getTotalPrice());
        LocalDateTime businessTime = row.getApprovalTime();
        if (businessTime == null) {
            businessTime = row.getOccurrenceTime();
        }
        model.setBusinessTime(businessTime);
        return model;
    }

    /**
     * 领用报表分桶键。
     */
    private String resolveDeptIssueReportBucketKey(
        SupplyRequest row,
        String dimension,
        Map<Long, Long> orgByLocation) {
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long itemId = row.getItemId();
            if (itemId == null) {
                return "ITEM:";
            }
            return "ITEM:" + itemId;
        }
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY.equals(dimension)) {
            Long locationId = row.getPurposeLocationId();
            if (locationId == null) {
                return "PHARMACY:";
            }
            return "PHARMACY:" + locationId;
        }
        Long orgId = this.resolveDeptIssueConsumingOrganizationId(row, orgByLocation);
        if (orgId == null) {
            return "DEPARTMENT:";
        }
        return "DEPARTMENT:" + orgId;
    }

    /**
     * 回填领用汇总维度主键。
     */
    private void fillDeptIssueReportSummaryIds(
        DeptIssueReportSummaryRow summary,
        SupplyRequest row,
        String dimension,
        Map<Long, Long> orgByLocation) {
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            summary.setItemId(row.getItemId());
            return;
        }
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY.equals(dimension)) {
            summary.setPurposeLocationId(row.getPurposeLocationId());
            return;
        }
        summary.setOrganizationId(this.resolveDeptIssueConsumingOrganizationId(row, orgByLocation));
        summary.setPurposeLocationId(row.getPurposeLocationId());
    }

    /**
     * 解析领用科室主键：优先落库 {@code consuming_organization_id}，历史单回落药房上级科室映射。
     *
     * @param row           供应请求行
     * @param orgByLocation 应用层预写：purposeLocationId → organizationId
     * @return 领用科室主键；均无则 null
     */
    private Long resolveDeptIssueConsumingOrganizationId(SupplyRequest row, Map<Long, Long> orgByLocation) {
        if (row == null) {
            return null;
        }
        // 新单：直接用落库领用科室
        if (row.getConsumingOrganizationId() != null) {
            return row.getConsumingOrganizationId();
        }
        // 历史单：回落药房 Location.organization_id（由应用层预写映射）
        if (row.getPurposeLocationId() == null || orgByLocation == null) {
            return null;
        }
        return orgByLocation.get(row.getPurposeLocationId());
    }

    /**
     * 领用为正、退领为负。
     *
     * @param row 供应请求行
     * @return 带符号数量
     */
    private BigDecimal resolveDeptIssueReportSignedQuantity(SupplyRequest row) {
        BigDecimal quantity = row.getItemQuantity();
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
        // 退领对冲净用量
        if (SupplyType.DEPT_ISSUE_RETURN.getCode().equals(row.getTypeEnum())) {
            return quantity.negate();
        }
        return quantity;
    }

    /**
     * 三类 OPENING 类型码列表。
     *
     * @return 不可变列表
     */
    public static List<String> openingTypeCodes() {
        return List.of(
            SupplyType.CABINET_OPENING.getCode(),
            SupplyType.PHARMACY_OPENING.getCode(),
            SupplyType.DEPT_OPENING.getCode());
    }

    /**
     * 同库是否存在非期初业务供应单（已提交及以上：审核中/已同意）（GAP-OPN-004 供应侧）。
     *
     * @param purposeLocationId 目的库房
     * @return 存在则为 true
     */
    public boolean existsNonOpeningBusinessForLocation(Long purposeLocationId) {
        if (purposeLocationId == null) {
            return false;
        }
        List<String> openingTypes = openingTypeCodes();
        List<String> blockingStatuses = List.of(
            SupplyStatus.APPROVAL.getCode(),
            SupplyStatus.AGREE.getCode());
        return supplyRequestRepository
            .existsByPurposeLocationIdAndTypeEnumNotInAndStatusEnumInAndIsDeletedFalse(
                purposeLocationId, openingTypes, blockingStatuses);
    }

    /**
     * 同库是否存在审核中的期初单（提交闸门 / 关闭前校验）。
     *
     * @param purposeLocationId 目的库房
     * @return 存在则为 true
     */
    public boolean existsOpeningInApprovalForLocation(Long purposeLocationId) {
        if (purposeLocationId == null) {
            return false;
        }
        return supplyRequestRepository
            .existsByPurposeLocationIdAndTypeEnumInAndStatusEnumAndIsDeletedFalse(
                purposeLocationId, openingTypeCodes(), SupplyStatus.APPROVAL.getCode());
    }

    /**
     * 同库当前有效 AGREE 期初单号（多行同号取首条 busNo）。
     *
     * @param purposeLocationId 目的库房
     * @return 单号；无则 empty
     */
    public Optional<String> findCurrentAgreedOpeningBusNo(Long purposeLocationId) {
        if (purposeLocationId == null) {
            return Optional.empty();
        }
        List<SupplyRequest> rows = supplyRequestRepository
            .findByPurposeLocationIdAndTypeEnumInAndStatusEnumAndIsDeletedFalse(
                purposeLocationId, openingTypeCodes(), SupplyStatus.AGREE.getCode());
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        SupplyRequest first = rows.get(0);
        if (first.getBusNo() == null || first.getBusNo().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(first.getBusNo().trim());
    }
}
