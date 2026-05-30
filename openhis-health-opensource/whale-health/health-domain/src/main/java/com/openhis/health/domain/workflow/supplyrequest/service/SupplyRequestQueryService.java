/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.supplyrequest.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoBatchQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByOriginalBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestIdListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.repository.SupplyRequestRepository;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyRequestDocumentListPredicateSupport;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 供应请求读侧查询服务。
 *
 * <p>职责：按业务单号、原单号、主键批量查询供应申请，并提供审批态门控与行编辑可行性判断；只读不写库。</p>
 *
 * <p>另提供 {@code wkf_supply_request} 上「按 {@code bus_no} 聚合」的通用单据列表能力（{@link #countDistinctBusNo}、{@link #listDistinctBusNoPage}、
 * {@link #listByBusNosWithFilter}），进货/订货/退货等各 AS 通过 {@link com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery}
 * 收窄 {@code type_enum} 与位置等条件后复用。</p>
 *
 * <p>读模型 {@link com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel} 中 {@code typeEnum}/{@code statusEnum} 等字段
 * 与实体列一致，均为持久化 {@code getCode()} 字符串（B.10.8.1）；本服务内与 {@link com.openhis.health.domain.share.enums.SupplyStatus} 比对时使用
 * {@code SupplyStatus.XXX.getCode()}。</p>
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
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(query.getBusNo().trim());
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
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoIn(query.getBusNoList());
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
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(query.getBusNo().trim());
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
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(busNo.trim());
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
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(busNo.trim());
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
     */
    public Page<WarehouseDocumentDetailQueryModel> pageWarehouseDocumentDetail(WarehouseDocumentDetailPageQuery query) {
        int springIndex = 0;
        int pageSize = 10;
        if (query != null) {
            springIndex = query.springPageIndex();
            pageSize = query.getBoundedPageSize();
        }
        Pageable pageable = PageRequest.of(springIndex, pageSize, Sort.by(Sort.Order.desc(FieldNameConstants.APPLY_TIME)));
        Specification<SupplyRequest> specification = (root, criteriaQuery, cb) -> cb.and(
            WarehouseDocumentDetailPagePredicateSupport.collectPagePredicates(root, cb, query).toArray(new Predicate[0]));
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
        model.setOriginalBusNo(entity.getOriginalBusNo());
        model.setSummaryId(entity.getSummaryId());
        model.setVersion(entity.getVersion());
        return model;
    }

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
        model.setItemTable(entity.getItemTable());
        model.setItemId(entity.getItemId());
        model.setItemQuantity(entity.getItemQuantity());
        model.setUnitCode(entity.getUnitCode());
        model.setLotNumber(entity.getLotNumber());
        model.setTraceNo(entity.getTraceNo());
        model.setPrice(entity.getPrice());
        model.setTotalPrice(entity.getTotalPrice());
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
}
