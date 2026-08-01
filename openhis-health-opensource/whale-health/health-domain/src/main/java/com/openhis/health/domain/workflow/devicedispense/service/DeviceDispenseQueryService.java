/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.service;

import com.openhis.health.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.DoctorStationOrderCatalogConstants;
import com.openhis.health.domain.share.constants.NurseWorkloadConstants;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.query.ConsumableUsageReportPageQuery;
import com.openhis.health.domain.workflow.devicedispense.queries.DeviceDispenseSettlementWindowQuery;
import com.openhis.health.domain.workflow.devicedispense.queries.DeviceDispensePageQuery;
import com.openhis.health.domain.workflow.devicedispense.queries.EncounterInfoPageQuery;
import com.openhis.health.domain.workflow.devicedispense.queries.model.*;
import com.openhis.health.domain.workflow.devicedispense.repository.DeviceDispenseRepository;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 高值/耗材发放读侧查询服务。
 *
 * <p>职责：发放单详情与列表、状态校验门控及工作台筛选；只读不写库。</p>
 *
 * <p>边界：仅依赖本聚合 {@link DeviceDispenseRepository}（B.6.4 仅本聚合 Repository）。
 * 护士工作量（AS-RPT-002）{@code hvcm_flag} 由
 * {@link com.openhis.health.application.report.nurseworkload.support.NurseWorkloadLoader} 预解析写入 query（B.6.4），
 * 见 DM-WKF-007 §6.2.20～6.2.22。</p>
 *
 * <p>与 {@code AS-OP-DOR-003} IF-DOR-11 对齐：提供同就诊候选库位上的耗材发放预占行批量加载（状态列为字符串码）。</p>
 *
 * <p>库房期初期末明细耗材发退药批量读见 {@link #listDeviceDispensesByLocationAndDispenseWindow}（**AS-INV-RPT-001**）。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceDispenseQueryService {

    private final DeviceDispenseRepository deviceDispenseRepository;


    /**
     * 按主键加载发放单读模型。
     *
     * @param id 发放单主键
     * @return 详情模型
     * @throws BusinessException 发放单不存在
     */
    public DeviceDispenseModel loadDeviceDispenseById(Long id) {
        return this.toDetailModel(this.mustGetDeviceDispenseEntity(id));
    }

    /**
     * 按耗材医嘱申请列出关联发放单主键（签退清理费用锚点用）。
     *
     * @param deviceReqId 医嘱申请主键
     * @return 发放单 id 列表；{@code deviceReqId} 为空时返回空表
     */
    public List<Long> listDeviceDispenseIdsByDeviceRequest(Long deviceReqId) {
        if (deviceReqId == null) {
            return new ArrayList<>();
        }
        Map<Long, List<Long>> idsByRequest = this.mapDeviceDispenseIdsByDeviceRequestIds(List.of(deviceReqId));
        List<Long> dispenseIds = idsByRequest.get(deviceReqId);
        if (dispenseIds == null) {
            return new ArrayList<>();
        }
        return dispenseIds;
    }

    /**
     * 按耗材医嘱申请主键集合批量列出关联发放单主键（护士站 IF-NUR-02 等，一次 IN 查询，对齐 A.9.1）。
     *
     * @param deviceReqIds 医嘱申请主键集合；{@code null} 或空时返回空映射
     * @return 医嘱申请 id → 发放单 id 列表（顺序不保证，取主发放行时由调用方 {@code Collections.min}）
     */
    public Map<Long, List<Long>> mapDeviceDispenseIdsByDeviceRequestIds(Collection<Long> deviceReqIds) {
        Map<Long, List<Long>> dispenseIdsByDeviceRequest = new HashMap<>();
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return dispenseIdsByDeviceRequest;
        }
        List<Long> distinctIds = new ArrayList<>();
        for (Long deviceReqId : deviceReqIds) {
            if (deviceReqId != null) {
                distinctIds.add(deviceReqId);
            }
        }
        if (distinctIds.isEmpty()) {
            return dispenseIdsByDeviceRequest;
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(distinctIds);
        for (DeviceDispense row : rows) {
            if (row.getDeviceReqId() == null || row.getId() == null) {
                continue;
            }
            dispenseIdsByDeviceRequest
                .computeIfAbsent(row.getDeviceReqId(), ignored -> new ArrayList<>())
                .add(row.getId());
        }
        return dispenseIdsByDeviceRequest;
    }

    /**
     * 医生站医嘱列表回显：对每个耗材医嘱取「发放主键升序第一条」的库位。
     *
     * @param deviceReqIds 医嘱申请主键集合
     * @return 医嘱 id → 首选发放库位 id
     */
    public Map<Long, Long> mapDeviceRequestToPrimaryDispenseLocationId(Collection<Long> deviceReqIds) {
        Map<Long, Long> locationByReq = new HashMap<>();
        Map<Long, Long> minDispenseIdByReq = new HashMap<>();
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return locationByReq;
        }
        List<Long> distinctIds = new ArrayList<>();
        // Loop：过滤空值构建有效ID列表
        for (Long id : deviceReqIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return locationByReq;
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(distinctIds);
        // Loop：遍历发放记录，按发放ID升序取首条库位作为首选库位
        for (DeviceDispense row : rows) {
            if (row.getDeviceReqId() == null || row.getLocationId() == null) {
                continue;
            }
            Long prevMin = minDispenseIdByReq.get(row.getDeviceReqId());
            if (prevMin == null || row.getId() < prevMin) {
                minDispenseIdByReq.put(row.getDeviceReqId(), row.getId());
                locationByReq.put(row.getDeviceReqId(), row.getLocationId());
            }
        }
        return locationByReq;
    }

    /**
     * 按就诊列出全部耗材发放单摘要。
     *
     * @param encounterId 就诊标识
     * @return 列表模型
     */
    public List<DeviceDispenseListModel> listDeviceDispensesByEncounter(Long encounterId) {
        List<DeviceDispense> rows = deviceDispenseRepository.findByEncounterId(encounterId);
        List<DeviceDispenseListModel> result = new ArrayList<>();
        // Loop：遍历就诊下全部发放记录，逐条转换为列表模型
        for (DeviceDispense row : rows) {
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 加载就诊下未软删的耗材发放摘要（仅本聚合 Repository）。
     *
     * <p>供按就诊批量读发放事实；退号收费门禁不再依赖本方法（药耗收口见门诊退费 IF-REF-04）。</p>
     *
     * @param encounterId 就诊主键
     * @return 列表模型；无记录时返回空表
     */
    public List<DeviceDispenseListModel> listActiveDeviceDispensesByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByEncounterId(encounterId);
        List<DeviceDispenseListModel> result = new ArrayList<>();
        for (DeviceDispense row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 医嘱开立目录：候选库位与目录耗材定义交集下，处于草稿/待发且未过 {@code limitTime} 的发放数量行（IF-DOR-11 预减口径，不限就诊）。
     *
     * @param candidateLocationIds   候选库房库位 id 列表
     * @param deviceDefinitionIds    当前页耗材定义 id 列表
     * @return 占用行；候选库位或定义集合为空时返回空列表
     */
    public List<DeviceDispenseOrderCatalogOccupancyModel> listDeviceDispensesForOrderCatalogOccupancy(
        List<Long> candidateLocationIds, List<Long> deviceDefinitionIds) {
        List<DeviceDispenseOrderCatalogOccupancyModel> result = new ArrayList<>();
        if (candidateLocationIds == null || candidateLocationIds.isEmpty()) {
            return result;
        }
        if (deviceDefinitionIds == null || deviceDefinitionIds.isEmpty()) {
            return result;
        }
        List<String> occupancyStatusCodes = new ArrayList<>();
        // Loop：遍历枚举集合，提取状态码构建IN条件
        for (DispenseStatus statusEnum : DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG) {
            occupancyStatusCodes.add(statusEnum.getCode());
        }
        LocalDateTime now = LocalDateTime.now();
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(candidateLocationIds));
            predicates.add(root.get(FieldNameConstants.DEVICE_DEF_ID).in(deviceDefinitionIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(occupancyStatusCodes));
            predicates.add(cb.greaterThan(root.get(FieldNameConstants.LIMIT_TIME), now));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<DeviceDispense> rows = deviceDispenseRepository.findAll(specification);
        // Loop：遍历占用行，逐条装配为订单目录占用模型
        for (DeviceDispense row : rows) {
            DeviceDispenseOrderCatalogOccupancyModel model = new DeviceDispenseOrderCatalogOccupancyModel();
            model.setDeviceDefId(row.getDeviceDefId());
            model.setLocationId(row.getLocationId());
            model.setQuantity(row.getQuantity());
            model.setUnitCode(row.getUnitCode());
            model.setStatusEnum(row.getStatusEnum());
            result.add(model);
        }
        return result;
    }

    /**
     * 必取发放单读模型（不存在抛业务异常）。
     *
     * @param id 发放单主键
     * @return 详情模型
     */
    public DeviceDispenseModel mustGetDeviceDispense(Long id) {
        return this.toDetailModel(this.mustGetDeviceDispenseEntity(id));
    }

    /**
     * 门控：是否允许进入配药/备货准备阶段（委托聚合根断言）。
     *
     * @param id 发放单主键
     * @return 校验结果（allow + 原因码）
     */
    public DeviceDispenseValidationModel validateDeviceDispensePreparable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertPreparable());
    }

    /**
     * 门控：是否允许实际发放出库。
     *
     * @param id 发放单主键
     * @return 校验结果
     */
    public DeviceDispenseValidationModel validateDeviceDispensable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertDispensable());
    }

    /**
     * 门控：是否允许作废尚未发放完成的单据。
     *
     * @param id 发放单主键
     * @return 校验结果
     */
    public DeviceDispenseValidationModel validateDeviceDispenseCancellable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertCancellableUndispensed());
    }

    /**
     * 门控：是否允许发起退库/退费关联（含幂等语义由聚合根承载）。
     *
     * @param id 发放单主键
     * @return 校验结果
     */
    public DeviceDispenseValidationModel validateDeviceDispenseRefundable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertRefundableAndIdempotent());
    }

    /**
     * 按诊疗操作/手术过程标识查询关联发放单列表。
     *
     * @param procedureId 过程主键
     * @return 发放单摘要列表
     */
    public List<DeviceDispenseListModel> getDevDispenseByProcedureId(Long procedureId) {
        List<DeviceDispense> rows = deviceDispenseRepository.findByProcedureId(procedureId);
        List<DeviceDispenseListModel> result = new ArrayList<>();
        // Loop：遍历诊疗操作关联发放记录，逐条转换为列表模型
        for (DeviceDispense row : rows) {
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 批量按耗材请求主键查询已生成的发放单（空入参避免无意义 IN 查询）。
     *
     * @param requestIdList 请求主键集合
     * @return 发放单摘要列表
     */
    public List<DeviceDispenseListModel> selectByRequestIdList(List<Long> requestIdList) {
        if (requestIdList == null || requestIdList.isEmpty()) {
            return new ArrayList<>();
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(requestIdList);
        List<DeviceDispenseListModel> result = new ArrayList<>();
        // Loop：遍历关联发放记录，逐条转换为列表模型
        for (DeviceDispense row : rows) {
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 解析耗材请求下主发放行 id（优先已发/已配药，避免 FIFO 替换后残留草稿行被 min(id) 误选）。
     *
     * @param deviceReqId 耗材请求主键
     * @return 主发放行 id；无则 null
     */
    public Long resolvePrimaryDispenseIdForDeviceRequest(Long deviceReqId) {
        DeviceDispenseListModel primary = this.resolvePrimaryDispenseForDeviceRequest(deviceReqId);
        if (primary == null) {
            return null;
        }
        return primary.getId();
    }

    /**
     * 解析耗材请求下主发放行（状态优先于 id 升序）。
     *
     * @param deviceReqId 耗材请求主键
     * @return 主发放行；无则 null
     */
    public DeviceDispenseListModel resolvePrimaryDispenseForDeviceRequest(Long deviceReqId) {
        if (deviceReqId == null) {
            return null;
        }
        List<DeviceDispenseListModel> rows = this.selectByRequestIdList(List.of(deviceReqId));
        return this.pickPrimaryDispenseRow(rows);
    }

    /**
     * 批量解析各耗材请求主发放状态码（与 {@link #resolvePrimaryDispenseForDeviceRequest} 同口径）。
     *
     * @param deviceReqIds 耗材请求主键集合
     * @return 请求 id → 主发放 status_enum
     */
    public Map<Long, String> mapPrimaryDispenseStatusByDeviceReqIds(Collection<Long> deviceReqIds) {
        Map<Long, String> statusByReqId = new HashMap<>();
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return statusByReqId;
        }
        List<Long> distinctIds = new ArrayList<>();
        for (Long deviceReqId : deviceReqIds) {
            if (deviceReqId != null) {
                distinctIds.add(deviceReqId);
            }
        }
        if (distinctIds.isEmpty()) {
            return statusByReqId;
        }
        List<DeviceDispenseListModel> rows = this.selectByRequestIdList(distinctIds);
        Map<Long, List<DeviceDispenseListModel>> rowsByReqId = new HashMap<>();
        for (DeviceDispenseListModel row : rows) {
            if (row == null || row.getDeviceReqId() == null) {
                continue;
            }
            rowsByReqId.computeIfAbsent(row.getDeviceReqId(), ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<Long, List<DeviceDispenseListModel>> entry : rowsByReqId.entrySet()) {
            DeviceDispenseListModel primary = this.pickPrimaryDispenseRow(entry.getValue());
            if (primary != null && primary.getStatusEnum() != null) {
                statusByReqId.put(entry.getKey(), primary.getStatusEnum());
            }
        }
        return statusByReqId;
    }

    /**
     * 工作台初始化（字典选项由上层注入，此处返回占位）。
     */
    public DeviceDispenseInitModel init(String context) {
        DeviceDispenseInitModel model = new DeviceDispenseInitModel();
        List<OptionDto> statusOptions = new ArrayList<>();
        long sort = 0L;
        // Loop：遍历发放状态枚举，构建前端下拉选项（排除UNKNOWN占位）
        for (DispenseStatus status : DispenseStatus.values()) {
            if (status == DispenseStatus.UNKNOWN) {
                continue;
            }
            sort++;
            statusOptions.add(new OptionDto(sort, String.valueOf(status.getCode()), status.name()));
        }
        model.setStatusOptions(statusOptions);
        return model;
    }

    /**
     * 就诊分页：按发放事实过滤；同一就诊多行发放时，分页行可能重复就诊维度。
     */
    public Page<EncounterInfoModel> getEncounterInfoListPage(EncounterInfoPageQuery query, Pageable pageable) {
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null && query.getEncounterId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
            }
            if (query != null && query.getPatientId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return deviceDispenseRepository.findAll(specification, pageable).map(row -> {
            EncounterInfoModel m = new EncounterInfoModel();
            m.setEncounterId(row.getEncounterId());
            m.setPatientId(row.getPatientId());
            return m;
        });
    }

    /**
     * 发放单分页列表：支持就诊、患者、状态与单号关键字组合过滤。
     *
     * @param query 筛选条件
     * @param pageable 分页
     * @return 分页列表模型
     */
    public Page<DeviceDispenseListModel> getDeviceDispenseOrderList(DeviceDispensePageQuery query, Pageable pageable) {
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (query.getEncounterId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
                }
                if (query.getPatientId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
                if (query.getStatusEnum() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
                }
                if (StringUtils.isNotEmpty(query.getBusNoKeyword())) {
                    String trimmed = query.getBusNoKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
                    // B.2.1：发放单号精确全等置顶
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmed,
                        List.of(cb.desc(root.get(FieldNameConstants.ID))),
                        root.get(FieldNameConstants.BUS_NO));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 精确优先 ORDER BY
        Pageable effective = pageable;
        if (query != null && StringUtils.isNotEmpty(query.getBusNoKeyword())
            && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return deviceDispenseRepository.findAll(specification, effective).map(this::toListModel);
    }

    /**
     * 库房期初期末明细耗材发退药批量读（**AS-INV-RPT-001** · **DM-WKF-007 §6.2.19**）。
     *
     * <p>默认状态为 {@link DispenseStatus#COMPLETED} 与 {@link DispenseStatus#REFUNDED}；发/退列拆分由应用层完成。</p>
     *
     * @param query 库位与发放时刻窗；{@code locationId} 为空时返回空列表
     * @return 发放明细行读模型
     */
    public List<DeviceDispenseSettlementLineQueryModel> listDeviceDispensesByLocationAndDispenseWindow(
        DeviceDispenseSettlementWindowQuery query) {
        if (query == null || query.getLocationId() == null) {
            return Collections.emptyList();
        }
        List<String> statusCodes = this.resolveSettlementStatusCodes(query.getStatusEnumWhitelist());
        Specification<DeviceDispense> specification = (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusCodes));
            if (query.getDispenseWindowStartInclusive() != null || query.getDispenseWindowEndInclusive() != null) {
                predicates.add(cb.isNotNull(root.get(FieldNameConstants.DISPENSE_TIME)));
            }
            if (query.getDispenseWindowStartInclusive() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.DISPENSE_TIME), query.getDispenseWindowStartInclusive()));
            }
            if (query.getDispenseWindowEndInclusive() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get(FieldNameConstants.DISPENSE_TIME), query.getDispenseWindowEndInclusive()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<DeviceDispense> rows = deviceDispenseRepository.findAll(
            specification, Sort.by(Sort.Order.asc(FieldNameConstants.DISPENSE_TIME), Sort.Order.asc(FieldNameConstants.ID)));
        return this.toSettlementLineModels(rows);
    }

    /**
     * 解析日结耗材发放状态白名单；未传时默认已完成发药与已退药。
     *
     * @param statusEnumWhitelist 调用方白名单
     * @return 持久化状态码列表
     */
    private List<String> resolveSettlementStatusCodes(List<String> statusEnumWhitelist) {
        if (statusEnumWhitelist != null && !statusEnumWhitelist.isEmpty()) {
            return new ArrayList<>(statusEnumWhitelist);
        }
        List<String> defaults = new ArrayList<>();
        defaults.add(DispenseStatus.COMPLETED.getCode());
        defaults.add(DispenseStatus.REFUNDED.getCode());
        return defaults;
    }

    /**
     * 发放实体列表映射为库房期初期末明细行读模型。
     *
     * @param rows 持久化行
     * @return 日结行列表
     */
    private List<DeviceDispenseSettlementLineQueryModel> toSettlementLineModels(List<DeviceDispense> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeviceDispenseSettlementLineQueryModel> models = new ArrayList<>();
        for (DeviceDispense row : rows) {
            if (row == null) {
                continue;
            }
            models.add(this.mapToSettlementLineModel(row));
        }
        return models;
    }

    /**
     * 单条发放行映射为日结读模型。
     *
     * @param row 发放实体
     * @return 日结行读模型
     */
    private DeviceDispenseSettlementLineQueryModel mapToSettlementLineModel(DeviceDispense row) {
        DeviceDispenseSettlementLineQueryModel model = new DeviceDispenseSettlementLineQueryModel();
        model.setId(row.getId());
        model.setBusNo(row.getBusNo());
        model.setDeviceDefId(row.getDeviceDefId());
        model.setLocationId(row.getLocationId());
        model.setQuantity(row.getQuantity());
        model.setUnitCode(row.getUnitCode());
        model.setLotNumber(row.getLotNumber());
        model.setDispenseTime(row.getDispenseTime());
        model.setStatusEnum(row.getStatusEnum());
        return model;
    }

    /**
     * 加载发放聚合根供断言与映射复用。
     *
     * @param id 主键
     * @return 实体
     * @throws BusinessException 不存在
     */
    private DeviceDispense mustGetDeviceDispenseEntity(Long id) {
        return deviceDispenseRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "耗材发放不存在"));
    }

    /**
     * 按主键加载未软删耗材发放行（不存在或已软删时 {@code null}）。
     *
     * @param id 发放主键
     * @return 发放行或 {@code null}
     */
    public DeviceDispense findById(Long id) {
        if (id == null) {
            return null;
        }
        DeviceDispense row = deviceDispenseRepository.findById(id).orElse(null);
        if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
            return null;
        }
        return row;
    }

    /**
     * 详情字段映射。
     *
     * @param row 实体
     * @return 读模型
     */
    private DeviceDispenseModel toDetailModel(DeviceDispense row) {
        DeviceDispenseModel model = new DeviceDispenseModel();
        model.setId(row.getId());
        model.setStatusEnum(row.getStatusEnum());
        model.setQuantity(row.getQuantity());
        model.setBusNo(row.getBusNo());
        model.setEncounterId(row.getEncounterId());
        model.setDeviceReqId(row.getDeviceReqId());
        model.setDeviceDefId(row.getDeviceDefId());
        model.setLocationId(row.getLocationId());
        model.setLotNumber(row.getLotNumber());
        model.setDispenseQuantity(row.getDispenseQuantity());
        model.setTraceNo(row.getTraceNo());
        model.setUnitCode(row.getUnitCode());
        return model;
    }

    /**
     * 列表行映射。
     *
     * @param row 实体
     * @return 列表模型
     */
    private DeviceDispenseListModel toListModel(DeviceDispense row) {
        DeviceDispenseListModel model = new DeviceDispenseListModel();
        model.setId(row.getId());
        model.setEncounterId(row.getEncounterId());
        model.setStatusEnum(row.getStatusEnum());
        model.setBusNo(row.getBusNo());
        model.setDeviceReqId(row.getDeviceReqId());
        return model;
    }

    /**
     * 从同一请求下多条发放行中选取主行：已发/已配药优先，同档取 id 最小。
     *
     * @param rows 发放行列表
     * @return 主发放行；空入参时 null
     */
    private DeviceDispenseListModel pickPrimaryDispenseRow(List<DeviceDispenseListModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        DeviceDispenseListModel primary = null;
        int primaryRank = -1;
        for (DeviceDispenseListModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            int rank = this.resolveDispenseStatusRank(row.getStatusEnum());
            if (primary == null
                || rank > primaryRank
                || (rank == primaryRank && row.getId() < primary.getId())) {
                primary = row;
                primaryRank = rank;
            }
        }
        return primary;
    }

    /**
     * 发放状态排序权重：已发 &gt; 已配药 &gt; 待发 &gt; 草稿。
     *
     * @param statusEnum 状态码
     * @return 权重；未知为 0
     */
    private int resolveDispenseStatusRank(String statusEnum) {
        if (DispenseStatus.COMPLETED.getCode().equals(statusEnum)
            || DispenseStatus.PART_COMPLETED.getCode().equals(statusEnum)) {
            return 5;
        }
        if (DispenseStatus.PREPARED.getCode().equals(statusEnum)) {
            return 4;
        }
        if (DispenseStatus.IN_PROGRESS.getCode().equals(statusEnum)) {
            return 3;
        }
        if (DispenseStatus.PREPARATION.getCode().equals(statusEnum)) {
            return 2;
        }
        if (DispenseStatus.DRAFT.getCode().equals(statusEnum)) {
            return 1;
        }
        return 0;
    }

    /**
     * 批量解析耗材发放单主键到耗材申请主键（医生站费用锚点归并，AS-OP-DOR-003 IF-DOR-10）。
     *
     * @param dispenseIds 发放单主键集合
     * @return 发放单 id → 耗材申请 id
     */
    public Map<Long, Long> mapDeviceDispenseIdsToDeviceReqIds(Collection<Long> dispenseIds) {
        Map<Long, Long> map = new HashMap<>();
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return map;
        }
        List<Long> idList = new ArrayList<>(dispenseIds);
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(idList);
        // Loop：遍历发放记录，构建发放ID到请求ID的映射
        for (DeviceDispense row : rows) {
            if (row.getDeviceReqId() != null) {
                map.put(row.getId(), row.getDeviceReqId());
            }
        }
        return map;
    }

    /**
     * 批量按主键加载耗材发放聚合行（报表进价批号解析；单次 IN 查询）。
     *
     * @param ids 发放单主键集合
     * @return 未软删实体列表；空入参返回空表
     */
    public List<DeviceDispense> listDeviceDispensesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<DeviceDispense> rows = this.deviceDispenseRepository.findByIdIn(new ArrayList<>(ids));
        List<DeviceDispense> result = new ArrayList<>();
        for (DeviceDispense row : rows) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 按耗材申请主键集合批量加载发放单（签发后迁移费用草稿用，AS-OP-DOR-003 IF-DOR-15）。
     *
     * @param deviceReqIds 耗材申请主键集合
     * @return 发放单实体列表
     */
    public List<DeviceDispense> listDeviceDispensesByDeviceReqIds(Collection<Long> deviceReqIds) {
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return List.of();
        }
        List<Long> idList = new ArrayList<>(deviceReqIds);
        return deviceDispenseRepository.findByDeviceReqIdIn(idList);
    }

    /**
     * 判断耗材是否存在非终态发放行（调价提交互斥 BR-PRC-SUB-003：在途发药）。
     *
     * @param deviceDefId 耗材定义主键
     * @return 存在在途发放时为 true
     */
    public boolean existsInTransitDispenseByDeviceDefId(Long deviceDefId) {
        if (deviceDefId == null) {
            return false;
        }
        // 非终态发放视为在途发药（与药品发放口径一致）
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.DEVICE_DEF_ID), deviceDefId));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(this.inTransitDispenseStatusCodes()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return deviceDispenseRepository.count(specification) > 0;
    }

    /**
     * 护士耗材工作量候选发放行（DM-WKF-007 §6.2.20；供 Support enrich 与汇总/明细复用）。
     *
     * @param query 预解析 scope 与 {@code deviceAutoInventoryDeductEnabled}
     * @return 时间窗与护士归属过滤后的发放行
     */
    public List<DeviceDispense> listNurseConsumableWorkloadCandidateDispenses(NurseWorkloadPageQuery query) {
        // Gate：Support 未写入 scope 或日期无效时直接空集
        if (query == null
            || query.getDateFrom() == null
            || query.getDateTo() == null
            || query.getResolvedEncounterScope() == null
            || query.getResolvedEncounterScope().isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：按全局开关决定纳入状态（关=PREPARED，开=COMPLETED）
        String statusCode = this.resolveNurseConsumableIncludedStatusCode(query.getDeviceAutoInventoryDeductEnabled());
        List<DeviceDispense> rows = this.deviceDispenseRepository.findByEncounterIdInAndStatusEnumInAndIsDeletedFalse(
            query.getResolvedEncounterScope(),
            List.of(statusCode));
        List<DeviceDispense> filtered = new ArrayList<>();
        LocalDateTime windowFrom = query.getDateFrom().atStartOfDay();
        LocalDateTime windowTo = LocalDateTime.of(query.getDateTo(), LocalTime.of(23, 59, 59, 999_999_999));
        for (DeviceDispense row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            LocalDateTime businessTime = this.resolveNurseConsumableBusinessTime(row, query.getDeviceAutoInventoryDeductEnabled());
            // 业务时间为空则无法归属统计窗，跳过
            if (businessTime == null) {
                continue;
            }
            if (businessTime.isBefore(windowFrom) || businessTime.isAfter(windowTo)) {
                continue;
            }
            Long attributedPractitionerId =
                this.resolveNurseConsumableAttributedPractitionerId(row, query.getDeviceAutoInventoryDeductEnabled());
            // 已选护士：仅保留归属护士匹配行
            if (query.getPractitionerId() != null && !query.getPractitionerId().equals(attributedPractitionerId)) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 护士耗材使用量按高/低值汇总（DM-WKF-007 §6.2.21；AS-RPT-002 IF-RPT-NWL-04）。
     *
     * @param query 预解析 scope 与 {@code hvcmFlagByDeviceDefId}
     * @return 汇总行
     */
    public List<NurseConsumableHvcmSummaryQueryModel> summarizeNurseConsumableWorkloadByHvcm(
        NurseWorkloadPageQuery query) {
        // Pre：汇总/明细共用候选发放行
        List<DeviceDispense> rows = this.listNurseConsumableWorkloadCandidateDispenses(query);
        // Asm：按 hvcm_flag 分桶累加实发数量
        Map<String, NurseConsumableHvcmSummaryQueryModel> bucketByHvcmKey = new LinkedHashMap<>();
        Map<Long, Boolean> hvcmByDeviceDefId = this.resolveHvcmFlagByDeviceDefId(query);
        for (DeviceDispense row : rows) {
            Boolean hvcmFlag = hvcmByDeviceDefId.get(row.getDeviceDefId());
            String bucketKey = this.buildHvcmBucketKey(hvcmFlag);
            NurseConsumableHvcmSummaryQueryModel summary = bucketByHvcmKey.get(bucketKey);
            if (summary == null) {
                summary = new NurseConsumableHvcmSummaryQueryModel();
                summary.setHvcmFlag(hvcmFlag);
                summary.setQuantityTotal(BigDecimal.ZERO);
                bucketByHvcmKey.put(bucketKey, summary);
            }
            summary.setQuantityTotal(summary.getQuantityTotal().add(this.resolveNurseConsumableQuantity(row)));
        }
        return new ArrayList<>(bucketByHvcmKey.values());
    }

    /**
     * 护士耗材使用量明细分页（DM-WKF-007 §6.2.22；AS-RPT-002 IF-RPT-NWL-05）。
     *
     * @param query 预解析 scope + 分页键
     * @return 明细分页；业务时间倒序
     */
    public Page<NurseConsumableWorkloadDetailQueryModel> pageNurseConsumableWorkloadDetail(NurseWorkloadPageQuery query) {
        // Pre：候选集按业务时间倒序后内存分页
        List<DeviceDispense> rows = this.listNurseConsumableWorkloadCandidateDispenses(query);
        rows.sort(Comparator.comparing(
            row -> this.resolveNurseConsumableBusinessTime(row, query.getDeviceAutoInventoryDeductEnabled()),
            Comparator.nullsLast(Comparator.reverseOrder())));
        int pageSize = this.resolveNurseWorkloadPageSize(query);
        int springIndex = 0;
        if (query != null) {
            springIndex = query.springPageIndex();
        }
        int fromIndex = springIndex * pageSize;
        if (fromIndex >= rows.size()) {
            Pageable pageable = PageRequest.of(springIndex, pageSize);
            return new PageImpl<>(Collections.emptyList(), pageable, rows.size());
        }
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        List<DeviceDispense> pageSlice = rows.subList(fromIndex, toIndex);
        Map<Long, Boolean> hvcmByDeviceDefId = this.resolveHvcmFlagByDeviceDefId(query);
        List<NurseConsumableWorkloadDetailQueryModel> models = new ArrayList<>();
        for (DeviceDispense row : pageSlice) {
            NurseConsumableWorkloadDetailQueryModel model = new NurseConsumableWorkloadDetailQueryModel();
            model.setDispenseId(row.getId());
            model.setDeviceReqId(row.getDeviceReqId());
            model.setDeviceDefId(row.getDeviceDefId());
            model.setEncounterId(row.getEncounterId());
            model.setPatientId(row.getPatientId());
            model.setUnitCode(row.getUnitCode());
            model.setQuantity(this.resolveNurseConsumableQuantity(row));
            model.setHvcmFlag(hvcmByDeviceDefId.get(row.getDeviceDefId()));
            model.setBusinessTime(this.resolveNurseConsumableBusinessTime(row, query.getDeviceAutoInventoryDeductEnabled()));
            model.setPractitionerId(
                this.resolveNurseConsumableAttributedPractitionerId(row, query.getDeviceAutoInventoryDeductEnabled()));
            models.add(model);
        }
        Pageable pageable = PageRequest.of(springIndex, pageSize);
        return new PageImpl<>(models, pageable, rows.size());
    }

    /**
     * 按全局自动扣库开关解析护士耗材统计纳入状态。
     *
     * @param deviceAutoInventoryDeductEnabled 开关开=true
     * @return PREPARED 或 COMPLETED 状态 code
     */
    private String resolveNurseConsumableIncludedStatusCode(Boolean deviceAutoInventoryDeductEnabled) {
        if (Boolean.TRUE.equals(deviceAutoInventoryDeductEnabled)) {
            return DispenseStatus.COMPLETED.getCode();
        }
        return DispenseStatus.PREPARED.getCode();
    }

    /**
     * 解析护士耗材统计业务时间（AS-RPT-002 BR-RPT-NWL-010～011）。
     *
     * @param row                              发放行
     * @param deviceAutoInventoryDeductEnabled 开关开=true
     * @return 业务时间；开关开且 dispense/prepare 皆空时返回 null
     */
    private LocalDateTime resolveNurseConsumableBusinessTime(
        DeviceDispense row,
        Boolean deviceAutoInventoryDeductEnabled) {
        if (Boolean.TRUE.equals(deviceAutoInventoryDeductEnabled)) {
            if (row.getDispenseTime() != null) {
                return row.getDispenseTime();
            }
            return row.getPrepareTime();
        }
        return row.getPrepareTime();
    }

    /**
     * 解析归属护士（开关关=preparer，开关开=performer）。
     *
     * @param row                              发放行
     * @param deviceAutoInventoryDeductEnabled 开关开=true
     * @return 归属护士执业者主键
     */
    private Long resolveNurseConsumableAttributedPractitionerId(
        DeviceDispense row,
        Boolean deviceAutoInventoryDeductEnabled) {
        if (Boolean.TRUE.equals(deviceAutoInventoryDeductEnabled)) {
            return row.getPerformerId();
        }
        return row.getPreparerId();
    }

    /**
     * 解析发放数量（实发量优先）。
     *
     * @param row 发放行
     * @return 数量；皆空时返回 0
     */
    private BigDecimal resolveNurseConsumableQuantity(DeviceDispense row) {
        if (row.getDispenseQuantity() != null) {
            return row.getDispenseQuantity();
        }
        if (row.getQuantity() != null) {
            return row.getQuantity();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 读取 Support 预解析的 hvcm map（B.6.4 跨聚合 enrich 结果）。
     *
     * @param query 护士工作量查询
     * @return deviceDefId → hvcmFlag
     */
    private Map<Long, Boolean> resolveHvcmFlagByDeviceDefId(NurseWorkloadPageQuery query) {
        if (query == null || query.getHvcmFlagByDeviceDefId() == null) {
            return Collections.emptyMap();
        }
        return query.getHvcmFlagByDeviceDefId();
    }

    /**
     * 高/低值/未 enrich 三档分桶键（LinkedHashMap 保序）。
     *
     * @param hvcmFlag 高值标志
     * @return 分桶键
     */
    private String buildHvcmBucketKey(Boolean hvcmFlag) {
        if (Boolean.TRUE.equals(hvcmFlag)) {
            return "HVCM_TRUE";
        }
        if (Boolean.FALSE.equals(hvcmFlag)) {
            return "HVCM_FALSE";
        }
        return "HVCM_NULL";
    }

    /**
     * 解析明细分页条数（AS-RPT-002 默认 20，硬上限 1000）。
     *
     * @param query 护士工作量查询
     * @return 每页条数
     */
    private int resolveNurseWorkloadPageSize(NurseWorkloadPageQuery query) {
        if (query == null || query.getPageSize() == null || query.getPageSize() < 1) {
            return NurseWorkloadConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(query.getPageSize(), 1000);
    }

    /**
     * 在途发放状态码：草稿、待发、配药中、部分完成、退药中等非终态。
     *
     * @return 状态 code 列表
     */
    private List<String> inTransitDispenseStatusCodes() {
        List<String> codes = new ArrayList<>();
        codes.add(DispenseStatus.DRAFT.getCode());
        codes.add(DispenseStatus.PREPARATION.getCode());
        codes.add(DispenseStatus.PREPARED.getCode());
        codes.add(DispenseStatus.IN_PROGRESS.getCode());
        codes.add(DispenseStatus.SUMMARIZED.getCode());
        codes.add(DispenseStatus.PART_COMPLETED.getCode());
        codes.add(DispenseStatus.ON_HOLD.getCode());
        codes.add(DispenseStatus.PENDING_REFUND.getCode());
        codes.add(DispenseStatus.PART_REFUND.getCode());
        return codes;
    }

    /**
     * 耗材使用报表患者侧明细分页（AS-RPT-005/006；DM-WKF-007 §6.2.23）。
     *
     * <p>纳入已完成发药与已退药；时间轴 {@code dispense_time}。仅本聚合 Repository。
     * 科室领用并集见 SupplyRequest；门诊/住院域由应用层预写 {@code resolvedEncounterScope}。</p>
     *
     * @param query 分页与筛选；就诊 scope 空列表拒查
     * @return 明细分页；发放时间倒序
     */
    public Page<ConsumableUsageDispenseLineQueryModel> pageConsumableUsageDispenseLines(
        ConsumableUsageReportPageQuery query) {
        if (query == null) {
            query = new ConsumableUsageReportPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        // Gate：空就诊 scope 表示门诊/住院域无命中，直接空页
        if (query.getResolvedEncounterScope() != null && query.getResolvedEncounterScope().isEmpty()) {
            Pageable emptyPageable = PageRequest.of(pageNo, pageSize);
            return new PageImpl<>(Collections.emptyList(), emptyPageable, 0L);
        }
        // Pre：规约过滤后仓储分页
        Specification<DeviceDispense> specification = this.buildConsumableUsageReportSpecification(query);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.DISPENSE_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<DeviceDispense> page = deviceDispenseRepository.findAll(specification, pageable);
        // Asm：本聚合字段直映；金额由应用层 ChargeItem enrich
        List<ConsumableUsageDispenseLineQueryModel> models = new ArrayList<>();
        for (DeviceDispense row : page.getContent()) {
            models.add(this.toConsumableUsageDispenseLineModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 耗材使用报表患者侧全量列表（导出；行数受 bounded pageSize 约束）。
     *
     * @param query 与分页同筛选
     * @return 明细行
     */
    public List<ConsumableUsageDispenseLineQueryModel> listConsumableUsageDispenseLines(
        ConsumableUsageReportPageQuery query) {
        if (query == null) {
            query = new ConsumableUsageReportPageQuery();
        }
        // Gate：空就诊 scope 拒查
        if (query.getResolvedEncounterScope() != null && query.getResolvedEncounterScope().isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：同明细分页规约，取 bounded 上限作导出窗
        int maxRows = query.getBoundedPageSize();
        Specification<DeviceDispense> specification = this.buildConsumableUsageReportSpecification(query);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.DISPENSE_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(0, maxRows, sort);
        Page<DeviceDispense> page = deviceDispenseRepository.findAll(specification, pageable);
        List<ConsumableUsageDispenseLineQueryModel> models = new ArrayList<>();
        for (DeviceDispense row : page.getContent()) {
            models.add(this.toConsumableUsageDispenseLineModel(row));
        }
        return models;
    }

    /**
     * 耗材使用报表患者侧净汇总（按患者/科室/库位/品规；DM-WKF-007 §6.2.24）。
     *
     * <p>不做药品类型式主汇总（AS-RPT-005/006）。科室维须预写 {@code organizationIdByEncounterId}。</p>
     *
     * @param query 筛选 + summaryDimension
     * @return 净汇总行
     */
    public List<ConsumableUsageDispenseSummaryRow> summarizeConsumableUsageDispense(
        ConsumableUsageReportPageQuery query) {
        if (query == null) {
            query = new ConsumableUsageReportPageQuery();
        }
        // Gate：空就诊 scope 拒查
        if (query.getResolvedEncounterScope() != null && query.getResolvedEncounterScope().isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：全量命中行（忽略分页键）后按维分桶
        Specification<DeviceDispense> specification = this.buildConsumableUsageReportSpecification(query);
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.DISPENSE_TIME));
        List<DeviceDispense> rows = deviceDispenseRepository.findAll(specification, sort);
        String dimension = StringUtils.trimToNull(query.getSummaryDimension());
        if (dimension == null) {
            dimension = ConsumableUsageReportConstants.SUMMARY_DIMENSION_PATIENT;
        }
        Map<Long, Long> orgByEncounter = query.getOrganizationIdByEncounterId();
        if (orgByEncounter == null) {
            orgByEncounter = Map.of();
        }
        // Asm：退药数量取负，累加净量
        Map<String, ConsumableUsageDispenseSummaryRow> bucketByKey = new LinkedHashMap<>();
        for (DeviceDispense row : rows) {
            if (row == null) {
                continue;
            }
            String bucketKey = this.resolveConsumableUsageBucketKey(row, dimension, orgByEncounter);
            ConsumableUsageDispenseSummaryRow summary = bucketByKey.get(bucketKey);
            if (summary == null) {
                summary = new ConsumableUsageDispenseSummaryRow();
                summary.setDimensionKey(bucketKey);
                summary.setSummaryDimension(dimension);
                summary.setNetQuantity(BigDecimal.ZERO);
                summary.setLineCount(0L);
                this.fillConsumableUsageSummaryIds(summary, row, dimension, orgByEncounter);
                bucketByKey.put(bucketKey, summary);
            }
            BigDecimal signedQty = this.resolveConsumableUsageSignedQuantity(row);
            summary.setNetQuantity(summary.getNetQuantity().add(signedQty));
            summary.setLineCount(summary.getLineCount() + 1L);
        }
        return new ArrayList<>(bucketByKey.values());
    }

    /**
     * 构建耗材使用报表规约：已发/已退 + 时间窗 + 库位/品规/就诊 scope。
     *
     * @param query 筛选
     * @return JPA 规约
     */
    private Specification<DeviceDispense> buildConsumableUsageReportSpecification(
        ConsumableUsageReportPageQuery query) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 报表事实：已完成发药 ∪ 已退药（与库房期初期末明细耗材列同精神）
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                DispenseStatus.COMPLETED.getCode(),
                DispenseStatus.PART_COMPLETED.getCode(),
                DispenseStatus.REFUNDED.getCode()));
            if (query.getBusinessTimeFrom() != null || query.getBusinessTimeTo() != null) {
                // 有时间窗时 dispense_time 必非空
                predicates.add(cb.isNotNull(root.get(FieldNameConstants.DISPENSE_TIME)));
            }
            if (query.getBusinessTimeFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.DISPENSE_TIME), query.getBusinessTimeFrom()));
            }
            if (query.getBusinessTimeTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get(FieldNameConstants.DISPENSE_TIME), query.getBusinessTimeTo()));
            }
            if (query.getLocationId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
            } else if (query.getResolvedLocationIds() != null) {
                // 空列表 = 关键字无命中，强制无结果
                if (query.getResolvedLocationIds().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(query.getResolvedLocationIds()));
                }
            }
            if (query.getResolvedDeviceDefIds() != null) {
                if (query.getResolvedDeviceDefIds().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(
                        root.get(FieldNameConstants.DEVICE_DEF_ID).in(query.getResolvedDeviceDefIds()));
                }
            }
            if (query.getResolvedEncounterScope() != null) {
                if (query.getResolvedEncounterScope().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(
                        root.get(FieldNameConstants.ENCOUNTER_ID).in(query.getResolvedEncounterScope()));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * 映射耗材使用明细行。
     */
    private ConsumableUsageDispenseLineQueryModel toConsumableUsageDispenseLineModel(DeviceDispense row) {
        ConsumableUsageDispenseLineQueryModel model = new ConsumableUsageDispenseLineQueryModel();
        model.setDispenseId(row.getId());
        model.setDeviceReqId(row.getDeviceReqId());
        model.setDeviceDefId(row.getDeviceDefId());
        model.setEncounterId(row.getEncounterId());
        model.setPatientId(row.getPatientId());
        model.setLocationId(row.getLocationId());
        model.setStatusEnum(row.getStatusEnum());
        model.setUnitCode(row.getUnitCode());
        model.setQuantity(this.resolveNurseConsumableQuantity(row));
        model.setDispenseTime(row.getDispenseTime());
        model.setLotNumber(row.getLotNumber());
        model.setPerformerId(row.getPerformerId());
        return model;
    }

    /**
     * 解析耗材使用汇总分桶键。
     */
    private String resolveConsumableUsageBucketKey(
        DeviceDispense row,
        String dimension,
        Map<Long, Long> orgByEncounter) {
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            Long orgId = null;
            if (row.getEncounterId() != null) {
                orgId = orgByEncounter.get(row.getEncounterId());
            }
            if (orgId == null) {
                return "DEPARTMENT:";
            }
            return "DEPARTMENT:" + orgId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)) {
            if (row.getLocationId() == null) {
                return "LOCATION:";
            }
            return "LOCATION:" + row.getLocationId();
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            if (row.getDeviceDefId() == null) {
                return "ITEM:";
            }
            return "ITEM:" + row.getDeviceDefId();
        }
        if (row.getPatientId() == null) {
            return "PATIENT:";
        }
        return "PATIENT:" + row.getPatientId();
    }

    /**
     * 回填耗材使用汇总维度主键。
     */
    private void fillConsumableUsageSummaryIds(
        ConsumableUsageDispenseSummaryRow summary,
        DeviceDispense row,
        String dimension,
        Map<Long, Long> orgByEncounter) {
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            if (row.getEncounterId() != null) {
                summary.setOrganizationId(orgByEncounter.get(row.getEncounterId()));
            }
            return;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)) {
            summary.setLocationId(row.getLocationId());
            return;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            summary.setDeviceDefId(row.getDeviceDefId());
            return;
        }
        summary.setPatientId(row.getPatientId());
    }

    /**
     * 耗材使用行符号数量：退药取负，其余取实发量。
     *
     * @param row 发放行
     * @return 带符号数量
     */
    private BigDecimal resolveConsumableUsageSignedQuantity(DeviceDispense row) {
        BigDecimal quantity = this.resolveNurseConsumableQuantity(row);
        // 已退药冲减净用量
        if (DispenseStatus.REFUNDED.getCode().equals(row.getStatusEnum())) {
            return quantity.negate();
        }
        return quantity;
    }

    /**
     * 将聚合根断言包装为「允许/拒绝 + 原因」供应用层统一展示。
     *
     * @param assertion 无参断言（内部抛 {@link BusinessException} 表示不允许）
     * @return 校验模型
     */
    private DeviceDispenseValidationModel validate(Runnable assertion) {
        DeviceDispenseValidationModel model = new DeviceDispenseValidationModel();
        try {
            assertion.run();
            model.setAllow(true);
            model.setReasonCode(null);
        } catch (BusinessException ex) {
            model.setAllow(false);
            model.setReasonCode(ex.getMessage());
        }
        return model;
    }
}
