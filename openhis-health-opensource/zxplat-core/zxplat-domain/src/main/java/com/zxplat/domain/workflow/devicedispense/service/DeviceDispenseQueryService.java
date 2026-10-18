/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.service;

import com.zxplat.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.zxplat.domain.share.constants.ConsumableUsageReportConstants;
import com.zxplat.domain.share.constants.DoctorStationOrderCatalogConstants;
import com.zxplat.domain.share.constants.NurseWorkloadConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.IdInQueryChunkSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.workflow.devicedispense.entity.DeviceDispense;
import com.zxplat.domain.workflow.devicedispense.query.ConsumableUsageReportPageQuery;
import com.zxplat.domain.workflow.devicedispense.queries.DeviceDispenseSettlementWindowQuery;
import com.zxplat.domain.workflow.devicedispense.queries.DeviceDispensePageQuery;
import com.zxplat.domain.workflow.devicedispense.queries.EncounterInfoPageQuery;
import com.zxplat.domain.workflow.devicedispense.queries.model.*;
import com.zxplat.domain.workflow.devicedispense.repository.DeviceDispenseRepository;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.zxplat.domain.workflow.devicedispense.support.NurseWorkloadDeviceDispenseReportCriteriaSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 高值/耗材发放读侧查询服务。
 *
 * <p>职责：发放单详情与列表、状态校验门控及工作台筛选；只读不写库。</p>
 *
 * <p>边界：仅依赖本聚合 {@link DeviceDispenseRepository}（B.6.4 仅本聚合 Repository）。
 * {@link EntityManager} 用于护士耗材明细分页 coalesce 业务时间排序（AS-RPT-002），
 * 以及耗材使用报表「按就诊+品规」合并分组 Criteria（COUNT/SUM/GROUP BY 真分页）。
 * 护士工作量（AS-RPT-002）{@code hvcm_flag} 由
 * {@link com.zxplat.domain.application.report.nurseworkload.support.NurseWorkloadLoader} 预解析写入 query（B.6.4），
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
    private final EntityManager entityManager;


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
     * 按就诊主键集合批量加载耗材发放摘要（仅本聚合 Repository）。
     *
     * <p>与 {@link #listDeviceDispensesByEncounter} 同口径：不过滤状态，软删在内存剔除，
     * 供护士站队列按「已收费请求 + 待发放/无发放单」判定红点。</p>
     *
     * @param encounterIds 本页就诊主键集合
     * @return 列表模型；空入参返回空表
     */
    public List<DeviceDispenseListModel> listDeviceDispensesByEncounterIds(Collection<Long> encounterIds) {
        List<DeviceDispenseListModel> result = new ArrayList<>();
        // Gate：无就诊主键则无需查发放单
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        List<DeviceDispense> rows = IdInQueryChunkSupport.loadByIdInChunks(
            encounterIds,
            chunkIds -> this.deviceDispenseRepository.findByEncounterIdIn(chunkIds));
        // 软删发放行不进入处置单侧栏
        for (DeviceDispense row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 医嘱开立目录：候选库位与目录耗材定义交集下，处于草稿/待发的发放数量行（IF-DOR-11 预减口径，不限就诊）。
     *
     * <p><strong>与药品差异</strong>：耗材待发预占<strong>不</strong>按 {@code limitTime} 过期释放；
     * 只要仍为 {@code DRAFT}/{@code PREPARATION} 且未逻辑删除，一直计入占用，直至护士执行推进或签退/取消释放
     * （避免预占过期后后开患者扣光签发批号、先开未执行患者护士站报库存不足）。药品侧仍为约 24h 窗，见
     * {@code MedicationDispenseQueryService#listMedicationDispensesForOrderCatalogOccupancy}。</p>
     *
     * @param candidateLocationIds   候选库房库位 id 列表
     * @param deviceDefinitionIds    当前页耗材定义 id 列表
     * @return 占用行；候选库位或定义集合为空时返回空列表
     */
    public List<DeviceDispenseOrderCatalogOccupancyModel> listDeviceDispensesForOrderCatalogOccupancy(
        List<Long> candidateLocationIds, List<Long> deviceDefinitionIds) {
        List<DeviceDispenseOrderCatalogOccupancyModel> result = new ArrayList<>();
        // Gate：无候选库位则无预占行
        if (candidateLocationIds == null || candidateLocationIds.isEmpty()) {
            return result;
        }
        // Gate：无耗材定义则无预占行
        if (deviceDefinitionIds == null || deviceDefinitionIds.isEmpty()) {
            return result;
        }
        List<String> occupancyStatusCodes = new ArrayList<>();
        // Loop：遍历枚举集合，提取状态码构建 IN 条件（仅草稿/待配药）
        for (DispenseStatus statusEnum : DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG) {
            occupancyStatusCodes.add(statusEnum.getCode());
        }
        // Pre：耗材预占不过期——故意不施加 now < limitTime（药品侧仍有该窗）
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(candidateLocationIds));
            predicates.add(root.get(FieldNameConstants.DEVICE_DEF_ID).in(deviceDefinitionIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(occupancyStatusCodes));
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
     * 批量解析各耗材请求主发放客户可见时刻（{@code dispense_time} 优先，否则 {@code prepare_time}）。
     *
     * <p>与护士划价列表/改时展示轴一致（AS-IP-NUR-005：耗材发放时间 = recorded 轴）。</p>
     *
     * @param deviceReqIds 耗材请求主键集合
     * @return 请求 id → 展示时刻；无主发放或时刻皆空时不入 Map
     */
    public Map<Long, LocalDateTime> mapPrimaryDisplayTimeByDeviceReqIds(Collection<Long> deviceReqIds) {
        Map<Long, LocalDateTime> timeByReqId = new HashMap<>();
        Map<Long, DeviceDispense> primaryByReqId = this.mapPrimaryDispenseEntityByDeviceReqIds(deviceReqIds);
        for (Map.Entry<Long, DeviceDispense> entry : primaryByReqId.entrySet()) {
            DeviceDispense row = entry.getValue();
            if (row == null) {
                continue;
            }
            LocalDateTime displayTime = row.getDispenseTime();
            // 未发仅拣配：展示 prepare_time
            if (displayTime == null) {
                displayTime = row.getPrepareTime();
            }
            if (displayTime != null) {
                timeByReqId.put(entry.getKey(), displayTime);
            }
        }
        return timeByReqId;
    }

    /**
     * 按主发放客户可见时刻左闭右开窗解析耗材请求主键（住院费用明细待结业务日轴）。
     *
     * @param displayFromInclusive 展示时刻起（含）
     * @param displayToExclusive   展示时刻止（不含）
     * @return deviceReqId → 展示时刻；窗非法或无命中时返回空 Map
     */
    public Map<Long, LocalDateTime> mapPrimaryDisplayTimeByDisplayTimeWindow(
        LocalDateTime displayFromInclusive,
        LocalDateTime displayToExclusive) {
        Map<Long, LocalDateTime> timeByReqId = new HashMap<>();
        if (displayFromInclusive == null || displayToExclusive == null) {
            return timeByReqId;
        }
        if (!displayFromInclusive.isBefore(displayToExclusive)) {
            return timeByReqId;
        }
        Specification<DeviceDispense> specification = (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            jakarta.persistence.criteria.Predicate dispenseInWindow = cb.and(
                cb.isNotNull(root.get(FieldNameConstants.DISPENSE_TIME)),
                cb.greaterThanOrEqualTo(root.get(FieldNameConstants.DISPENSE_TIME), displayFromInclusive),
                cb.lessThan(root.get(FieldNameConstants.DISPENSE_TIME), displayToExclusive));
            jakarta.persistence.criteria.Predicate prepareInWindow = cb.and(
                cb.isNull(root.get(FieldNameConstants.DISPENSE_TIME)),
                cb.isNotNull(root.get(FieldNameConstants.PREPARE_TIME)),
                cb.greaterThanOrEqualTo(root.get(FieldNameConstants.PREPARE_TIME), displayFromInclusive),
                cb.lessThan(root.get(FieldNameConstants.PREPARE_TIME), displayToExclusive));
            predicates.add(cb.or(dispenseInWindow, prepareInWindow));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<DeviceDispense> candidates = this.deviceDispenseRepository.findAll(specification);
        LinkedHashSet<Long> deviceReqIds = new LinkedHashSet<>();
        for (DeviceDispense row : candidates) {
            if (row != null && row.getDeviceReqId() != null) {
                deviceReqIds.add(row.getDeviceReqId());
            }
        }
        if (deviceReqIds.isEmpty()) {
            return timeByReqId;
        }
        Map<Long, LocalDateTime> primaryDisplayByReqId = this.mapPrimaryDisplayTimeByDeviceReqIds(deviceReqIds);
        for (Map.Entry<Long, LocalDateTime> entry : primaryDisplayByReqId.entrySet()) {
            LocalDateTime displayTime = entry.getValue();
            if (displayTime == null) {
                continue;
            }
            if (displayTime.isBefore(displayFromInclusive) || !displayTime.isBefore(displayToExclusive)) {
                continue;
            }
            timeByReqId.put(entry.getKey(), displayTime);
        }
        return timeByReqId;
    }

    /**
     * 批量解析各耗材请求主发放主键（改时写路径锚定）。
     *
     * @param deviceReqIds 耗材请求主键集合
     * @return 请求 id → 主发放 id
     */
    public Map<Long, Long> mapPrimaryDispenseIdByDeviceReqIds(Collection<Long> deviceReqIds) {
        Map<Long, Long> idByReqId = new HashMap<>();
        Map<Long, DeviceDispense> primaryByReqId = this.mapPrimaryDispenseEntityByDeviceReqIds(deviceReqIds);
        for (Map.Entry<Long, DeviceDispense> entry : primaryByReqId.entrySet()) {
            DeviceDispense row = entry.getValue();
            if (row == null || row.getId() == null) {
                continue;
            }
            idByReqId.put(entry.getKey(), row.getId());
        }
        return idByReqId;
    }

    /**
     * 按请求批量选取主发放实体（已发/已配优先，同档取 id 最小）。
     *
     * @param deviceReqIds 耗材请求主键集合
     * @return 请求 id → 主发放实体
     */
    private Map<Long, DeviceDispense> mapPrimaryDispenseEntityByDeviceReqIds(Collection<Long> deviceReqIds) {
        Map<Long, DeviceDispense> primaryByReqId = new HashMap<>();
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return primaryByReqId;
        }
        List<Long> distinctIds = new ArrayList<>();
        for (Long deviceReqId : deviceReqIds) {
            if (deviceReqId != null) {
                distinctIds.add(deviceReqId);
            }
        }
        if (distinctIds.isEmpty()) {
            return primaryByReqId;
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(distinctIds);
        Map<Long, List<DeviceDispense>> rowsByReqId = new HashMap<>();
        for (DeviceDispense row : rows) {
            if (row == null || row.getDeviceReqId() == null) {
                continue;
            }
            rowsByReqId.computeIfAbsent(row.getDeviceReqId(), ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<Long, List<DeviceDispense>> entry : rowsByReqId.entrySet()) {
            DeviceDispense primary = null;
            int primaryRank = -1;
            for (DeviceDispense row : entry.getValue()) {
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
            if (primary != null) {
                primaryByReqId.put(entry.getKey(), primary);
            }
        }
        return primaryByReqId;
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
        model.setBasedOnId(row.getBasedOnId());
        model.setLotNumber(row.getLotNumber());
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
     * 批量按主键加载耗材发放聚合行（报表进价批号解析）。
     *
     * <p>主键过多时按 {@link com.zxplat.domain.share.constants.GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批 IN。</p>
     *
     * @param ids 发放单主键集合
     * @return 未软删实体列表；空入参返回空表
     */
    public List<DeviceDispense> listDeviceDispensesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<DeviceDispense> rows = IdInQueryChunkSupport.loadByIdInChunks(
            ids,
            chunkIds -> this.deviceDispenseRepository.findByIdIn(new ArrayList<>(chunkIds)));
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
     * <p>主键过多时按分批 IN，避免超长参数。</p>
     *
     * @param deviceReqIds 耗材申请主键集合
     * @return 发放单实体列表
     */
    public List<DeviceDispense> listDeviceDispensesByDeviceReqIds(Collection<Long> deviceReqIds) {
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return List.of();
        }
        return IdInQueryChunkSupport.loadByIdInChunks(
            deviceReqIds,
            chunkIds -> deviceDispenseRepository.findByDeviceReqIdIn(new ArrayList<>(chunkIds)));
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
     * 判断同耗材定义同批号是否存在未软删发放行（任意状态；BR-WHS-IN-018）。
     *
     * @param deviceDefId 耗材定义主键
     * @param lotNumber   批号字面值
     * @return 存在则为 true
     */
    public boolean existsByDeviceDefIdAndLotNumber(Long deviceDefId, String lotNumber) {
        // Gate：键不全视为无发放
        if (deviceDefId == null || StringUtils.isEmpty(lotNumber)) {
            return false;
        }
        return deviceDispenseRepository.existsByDeviceDefIdAndLotNumberAndIsDeletedFalse(
            deviceDefId, lotNumber.trim());
    }

    /**
     * 护士耗材工作量候选发放行（DM-WKF-007 §6.2.20；供 Support enrich 与汇总/明细复用）。
     *
     * @param query 预解析 scope 与 {@code deviceAutoInventoryDeductEnabled}
     * @return 时间窗与护士归属过滤后的发放行
     */
    public List<DeviceDispense> listNurseConsumableWorkloadCandidateDispenses(NurseWorkloadPageQuery query) {
        // Pre：同窗内已物化候选发放行则直接复用
        if (query != null && query.getCachedNurseConsumableCandidatesForRequest() != null) {
            return this.castNurseConsumableCandidateCache(query.getCachedNurseConsumableCandidatesForRequest());
        }
        // Gate：Support 未写入 scope 或日期无效时直接空集
        if (query == null
            || query.getDateFrom() == null
            || query.getDateTo() == null
            || query.getResolvedEncounterScope() == null
            || query.getResolvedEncounterScope().isEmpty()) {
            return this.storeNurseConsumableCandidateCache(query, Collections.emptyList());
        }
        // Gate：默认登录科室下无护士时直接空集，禁止回落全院
        if (query.getPractitionerId() == null
            && query.getPractitionerIds() != null
            && query.getPractitionerIds().isEmpty()) {
            return this.storeNurseConsumableCandidateCache(query, Collections.emptyList());
        }
        // Pre：与明细分页共用同一套 Criteria（汇总数量 = 明细 total；禁止 Java/SQL 双轨）
        List<DeviceDispense> filtered = this.listNurseConsumableCandidateDispensesFromDatabase(query);
        return this.storeNurseConsumableCandidateCache(query, filtered);
    }

    /**
     * 按护士耗材报表规约加载全量候选发放行（业务时间倒序；与明细分页同口径）。
     *
     * @param query 预解析 scope
     * @return 命中行；无 scope 时空列表
     */
    private List<DeviceDispense> listNurseConsumableCandidateDispensesFromDatabase(NurseWorkloadPageQuery query) {
        Specification<DeviceDispense> specification =
            NurseWorkloadDeviceDispenseReportCriteriaSupport.buildCandidateSpecification(query);
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<DeviceDispense> dataQuery = cb.createQuery(DeviceDispense.class);
        Root<DeviceDispense> dataRoot = dataQuery.from(DeviceDispense.class);
        Predicate dataPredicate = specification.toPredicate(dataRoot, dataQuery, cb);
        if (dataPredicate != null) {
            dataQuery.where(dataPredicate);
        }
        Boolean deductEnabled = null;
        if (query != null) {
            deductEnabled = query.getDeviceAutoInventoryDeductEnabled();
        }
        Expression<LocalDateTime> businessTime =
            NurseWorkloadDeviceDispenseReportCriteriaSupport.resolveBusinessTimeExpression(
                dataRoot, cb, deductEnabled);
        dataQuery.orderBy(cb.desc(businessTime));
        return this.entityManager.createQuery(dataQuery).getResultList();
    }

    /**
     * 写入同窗耗材候选发放缓存并返回同一列表引用。
     *
     * @param query    护士工作量查询
     * @param filtered 候选发放行
     * @return {@code filtered}
     */
    private List<DeviceDispense> storeNurseConsumableCandidateCache(
        NurseWorkloadPageQuery query,
        List<DeviceDispense> filtered) {
        List<DeviceDispense> sortedRows = filtered;
        Boolean deviceAutoInventoryDeductEnabled = null;
        if (query != null) {
            deviceAutoInventoryDeductEnabled = query.getDeviceAutoInventoryDeductEnabled();
        }
        // Pre：同窗缓存前按业务时间倒序，分页/全量禁止重复 sort
        if (sortedRows != null && sortedRows.size() > 1) {
            sortedRows = new ArrayList<>(sortedRows);
            Boolean deductFlag = deviceAutoInventoryDeductEnabled;
            sortedRows.sort(Comparator.comparing(
                row -> this.resolveNurseConsumableBusinessTime(row, deductFlag),
                Comparator.nullsLast(Comparator.reverseOrder())));
        }
        if (query != null) {
            query.setCachedNurseConsumableCandidatesForRequest(sortedRows);
        }
        return sortedRows;
    }

    /**
     * 读取同窗耗材候选发放缓存。
     *
     * @param cached 缓存列表
     * @return DeviceDispense 列表
     */
    @SuppressWarnings("unchecked")
    private List<DeviceDispense> castNurseConsumableCandidateCache(List<?> cached) {
        if (cached == null || cached.isEmpty()) {
            return Collections.emptyList();
        }
        return (List<DeviceDispense>) cached;
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
        int pageSize = this.resolveNurseWorkloadPageSize(query);
        int springIndex = 0;
        if (query != null) {
            springIndex = query.springPageIndex();
        }
        Pageable pageable = PageRequest.of(springIndex, pageSize);
        // Pre：同窗候选缓存命中则内存切片（汇总+明细分页同窗复用）
        if (query != null && query.getCachedNurseConsumableCandidatesForRequest() != null) {
            List<DeviceDispense> rows = this.castNurseConsumableCandidateCache(query.getCachedNurseConsumableCandidatesForRequest());
            int fromIndex = springIndex * pageSize;
            if (fromIndex >= rows.size()) {
                return new PageImpl<>(Collections.emptyList(), pageable, rows.size());
            }
            int toIndex = Math.min(fromIndex + pageSize, rows.size());
            List<NurseConsumableWorkloadDetailQueryModel> pageModels =
                this.toNurseConsumableWorkloadDetailModels(rows.subList(fromIndex, toIndex), query);
            return new PageImpl<>(pageModels, pageable, rows.size());
        }
        // Pre：明细分页 DB 真分页（业务时间 coalesce 倒序；禁止全窗 findAll 后 subList）
        Page<DeviceDispense> entityPage = this.pageNurseConsumableCandidateDispensesAtDatabase(query, springIndex, pageSize);
        List<NurseConsumableWorkloadDetailQueryModel> pageModels =
            this.toNurseConsumableWorkloadDetailModels(entityPage.getContent(), query);
        return new PageImpl<>(pageModels, pageable, entityPage.getTotalElements());
    }

    /**
     * 护士耗材候选发放 DB 分页（coalesce 业务时间倒序 + COUNT）。
     *
     * @param query        预解析 scope
     * @param springIndex  Spring 页码（0-based）
     * @param pageSize     每页条数
     * @return 当前页实体 + total
     */
    private Page<DeviceDispense> pageNurseConsumableCandidateDispensesAtDatabase(
        NurseWorkloadPageQuery query,
        int springIndex,
        int pageSize) {
        Specification<DeviceDispense> specification =
            NurseWorkloadDeviceDispenseReportCriteriaSupport.buildCandidateSpecification(query);
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<DeviceDispense> countRoot = countQuery.from(DeviceDispense.class);
        Predicate countPredicate = specification.toPredicate(countRoot, countQuery, cb);
        countQuery.select(cb.count(countRoot));
        if (countPredicate != null) {
            countQuery.where(countPredicate);
        }
        Long totalElements = this.entityManager.createQuery(countQuery).getSingleResult();
        if (totalElements == null) {
            totalElements = 0L;
        }
        CriteriaQuery<DeviceDispense> dataQuery = cb.createQuery(DeviceDispense.class);
        Root<DeviceDispense> dataRoot = dataQuery.from(DeviceDispense.class);
        Predicate dataPredicate = specification.toPredicate(dataRoot, dataQuery, cb);
        if (dataPredicate != null) {
            dataQuery.where(dataPredicate);
        }
        Boolean deductEnabled = null;
        if (query != null) {
            deductEnabled = query.getDeviceAutoInventoryDeductEnabled();
        }
        Expression<LocalDateTime> businessTime =
            NurseWorkloadDeviceDispenseReportCriteriaSupport.resolveBusinessTimeExpression(
                dataRoot, cb, deductEnabled);
        Order orderByBusinessTime = cb.desc(businessTime);
        dataQuery.orderBy(orderByBusinessTime);
        TypedQuery<DeviceDispense> typedQuery = this.entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult(springIndex * pageSize);
        typedQuery.setMaxResults(pageSize);
        List<DeviceDispense> content = typedQuery.getResultList();
        Pageable pageable = PageRequest.of(springIndex, pageSize);
        return new PageImpl<>(content, pageable, totalElements);
    }

    /**
     * 护士耗材使用量明细全量列表（汇总页脚金额 / 卡片分桶复用；与分页同口径）。
     *
     * @param query 预解析 scope
     * @return 按业务时间倒序的明细
     */
    public List<NurseConsumableWorkloadDetailQueryModel> listNurseConsumableWorkloadDetail(NurseWorkloadPageQuery query) {
        List<DeviceDispense> rows = this.listNurseConsumableWorkloadCandidateDispenses(query);
        return this.toNurseConsumableWorkloadDetailModels(rows, query);
    }

    /**
     * 将耗材发放切片装配为明细读模型（hvcm / 业务时间 / 归属护士）。
     *
     * @param dispenseSlice 已排序的发放切片
     * @param query         含 hvcm enrich map 与扣库开关
     * @return 明细读模型
     */
    private List<NurseConsumableWorkloadDetailQueryModel> toNurseConsumableWorkloadDetailModels(
        List<DeviceDispense> dispenseSlice,
        NurseWorkloadPageQuery query) {
        if (dispenseSlice == null || dispenseSlice.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Boolean> hvcmByDeviceDefId = this.resolveHvcmFlagByDeviceDefId(query);
        List<NurseConsumableWorkloadDetailQueryModel> models = new ArrayList<>(dispenseSlice.size());
        for (DeviceDispense row : dispenseSlice) {
            if (row == null) {
                continue;
            }
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
        return models;
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
     * 判断归属护士是否落入当前筛选（单护士优先；否则科室护士 IN；均未设则不过滤）。
     *
     * @param query                    护士工作量查询
     * @param attributedPractitionerId 行归属护士主键
     * @return {@code true} 表示计入候选
     */
    private boolean matchesNurseWorkloadAttributedPractitioner(
        NurseWorkloadPageQuery query,
        Long attributedPractitionerId) {
        if (query.getPractitionerId() == null && query.getPractitionerIds() == null) {
            return true;
        }
        if (attributedPractitionerId == null) {
            return false;
        }
        if (query.getPractitionerId() != null) {
            return query.getPractitionerId().equals(attributedPractitionerId);
        }
        for (Long allowedId : query.getPractitionerIds()) {
            if (attributedPractitionerId.equals(allowedId)) {
                return true;
            }
        }
        return false;
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
     * 耗材使用报表患者侧「按就诊+品规[+单位]」合并分组分页（真分页；禁止全窗物化再 subList）。
     *
     * <p>筛选同 {@link #pageConsumableUsageDispenseLines}；额外要求 {@code encounter_id}/{@code device_def_id}
     * 非空。按组 {@code MAX(dispense_time) DESC} 排序；总数为分组条数而非发放行数。
     * 分组行 {@code netQuantity} 占位 0，由应用层页内回载明细后汇总（禁 SQL SUM(CASE)）。</p>
     *
     * @param query 分页与筛选；就诊 scope 空列表拒查
     * @return 合并分组页
     */
    public Page<ConsumableUsageDispenseMergedGroupQueryModel> pageConsumableUsageDispenseMergedByEncounterAndDevice(
        ConsumableUsageReportPageQuery query) {
        if (query == null) {
            query = new ConsumableUsageReportPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        // Gate：空就诊 scope 表示门诊/住院域无命中，直接空页
        if (query.getResolvedEncounterScope() != null && query.getResolvedEncounterScope().isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        // Pre：分组总数（GROUP BY 键结果集 size；禁 countDistinct(concat)）
        long total = this.countConsumableUsageMergedGroups(cb, query);
        if (total == 0L) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        // Pre：本页分组聚合行
        List<ConsumableUsageDispenseMergedGroupQueryModel> models =
            this.queryConsumableUsageMergedGroups(cb, query, pageNo, pageSize);
        return new PageImpl<>(models, pageable, total);
    }

    /**
     * 耗材使用报表患者侧「按就诊+品规[+单位]」合并分组有界列表（导出；行数受 bounded pageSize 约束）。
     *
     * @param query 与分页同筛选；{@code pageSize} 作为导出上限
     * @return 合并分组行（按 max(dispense_time) 倒序）
     */
    public List<ConsumableUsageDispenseMergedGroupQueryModel> listConsumableUsageDispenseMergedByEncounterAndDevice(
        ConsumableUsageReportPageQuery query) {
        if (query == null) {
            query = new ConsumableUsageReportPageQuery();
        }
        // Gate：空就诊 scope 拒查
        if (query.getResolvedEncounterScope() != null && query.getResolvedEncounterScope().isEmpty()) {
            return Collections.emptyList();
        }
        int maxRows = query.getBoundedPageSize();
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        return this.queryConsumableUsageMergedGroups(cb, query, 0, maxRows);
    }

    /**
     * 按本页合并键的 encounter/deviceDef 集合回载患者侧发放明细（同报表筛选 + IN 收窄）。
     *
     * <p>供应用层对本页分组回刷进售价后按精确键再汇总；页内 IN 交集可能带回少量非本页键行，
     * 由应用层按 encounter+deviceDef+unit 精确归组丢弃。</p>
     *
     * @param query         同明细分页筛选（忽略其分页键）
     * @param encounterIds  本页分组就诊主键；空则空列表
     * @param deviceDefIds  本页分组品规主键；空则空列表
     * @return 命中发放明细行
     */
    public List<ConsumableUsageDispenseLineQueryModel> listConsumableUsageDispenseLinesForMergedKeys(
        ConsumableUsageReportPageQuery query,
        Set<Long> encounterIds,
        Set<Long> deviceDefIds) {
        if (query == null) {
            query = new ConsumableUsageReportPageQuery();
        }
        // Gate：空键集合或空就诊 scope → 无回载
        if (encounterIds == null || encounterIds.isEmpty() || deviceDefIds == null || deviceDefIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (query.getResolvedEncounterScope() != null && query.getResolvedEncounterScope().isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> encounterIdSet = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                encounterIdSet.add(encounterId);
            }
        }
        LinkedHashSet<Long> deviceDefIdSet = new LinkedHashSet<>();
        for (Long deviceDefId : deviceDefIds) {
            if (deviceDefId != null) {
                deviceDefIdSet.add(deviceDefId);
            }
        }
        if (encounterIdSet.isEmpty() || deviceDefIdSet.isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：报表规约 + 本页键 IN（页内规模，允许全取）
        Specification<DeviceDispense> specification = this.buildConsumableUsageReportSpecification(query);
        Specification<DeviceDispense> keySpec = (root, cq, cb) -> cb.and(
            root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIdSet),
            root.get(FieldNameConstants.DEVICE_DEF_ID).in(deviceDefIdSet),
            cb.isNotNull(root.get(FieldNameConstants.ENCOUNTER_ID)),
            cb.isNotNull(root.get(FieldNameConstants.DEVICE_DEF_ID)));
        List<DeviceDispense> rows = this.deviceDispenseRepository.findAll(specification.and(keySpec));
        List<ConsumableUsageDispenseLineQueryModel> models = new ArrayList<>();
        for (DeviceDispense row : rows) {
            models.add(this.toConsumableUsageDispenseLineModel(row));
        }
        return models;
    }

    /**
     * 统计合并分组总数（encounter+deviceDef+unit 键）。
     *
     * <p>与 {@link #queryConsumableUsageMergedGroups} 同筛选、同 GROUP BY；用分组键结果集 size 计数，
     * 禁止 {@code countDistinct(concat(cast...))}（PG/Hibernate 易炸连接 → Unable to rollback）。</p>
     *
     * @param cb    CriteriaBuilder
     * @param query 筛选
     * @return 分组条数
     */
    private long countConsumableUsageMergedGroups(CriteriaBuilder cb, ConsumableUsageReportPageQuery query) {
        CriteriaQuery<Object[]> keyCq = cb.createQuery(Object[].class);
        Root<DeviceDispense> root = keyCq.from(DeviceDispense.class);
        List<Predicate> predicates = this.buildConsumableUsageReportPredicates(root, cb, query);
        // Gate：合并键字段必非空
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.ENCOUNTER_ID)));
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.DEVICE_DEF_ID)));
        // Asm：仅投影分组键；计数取结果条数（同 SupplyRequest 按 bus_no 分组分页口径）
        keyCq.multiselect(
            root.get(FieldNameConstants.ENCOUNTER_ID),
            root.get(FieldNameConstants.DEVICE_DEF_ID),
            root.get(FieldNameConstants.UNIT_CODE));
        keyCq.where(cb.and(predicates.toArray(new Predicate[0])));
        keyCq.groupBy(
            root.get(FieldNameConstants.ENCOUNTER_ID),
            root.get(FieldNameConstants.DEVICE_DEF_ID),
            root.get(FieldNameConstants.UNIT_CODE));
        List<Object[]> keyRows = this.entityManager.createQuery(keyCq).getResultList();
        if (keyRows == null) {
            return 0L;
        }
        return keyRows.size();
    }

    /**
     * 查询一页（或有界）合并分组键行（不含符号净量；净量由应用层页内回载明细汇总）。
     *
     * <p>投影仅分组键 + 组内 max(patient/location/time/id) + count；禁止 SQL 侧
     * {@code SUM(CASE 退药取负)}（Criteria CASE+SUM 在本库方言下会打挂连接）。</p>
     *
     * @param cb       CriteriaBuilder
     * @param query    筛选
     * @param pageNo   Spring 0 基页码
     * @param pageSize 页大小/上限
     * @return 分组读模型列表（netQuantity 先置 0，由应用层回刷）
     */
    private List<ConsumableUsageDispenseMergedGroupQueryModel> queryConsumableUsageMergedGroups(
        CriteriaBuilder cb,
        ConsumableUsageReportPageQuery query,
        int pageNo,
        int pageSize) {
        CriteriaQuery<Object[]> dataCq = cb.createQuery(Object[].class);
        Root<DeviceDispense> root = dataCq.from(DeviceDispense.class);
        List<Predicate> predicates = this.buildConsumableUsageReportPredicates(root, cb, query);
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.ENCOUNTER_ID)));
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.DEVICE_DEF_ID)));
        // 组内最晚发放时刻：Temporal 用 greatest（CriteriaBuilder.max 仅 Number）
        Expression<LocalDateTime> maxDispenseTime =
            cb.greatest(root.<LocalDateTime>get(FieldNameConstants.DISPENSE_TIME));
        // Asm：与 SupplyRequest#listDistinctBusNoPage 同构——分组键 + 排序聚合，不做符号数量 SUM
        dataCq.multiselect(
            root.get(FieldNameConstants.ENCOUNTER_ID),
            root.get(FieldNameConstants.DEVICE_DEF_ID),
            root.get(FieldNameConstants.UNIT_CODE),
            cb.max(root.get(FieldNameConstants.PATIENT_ID)),
            cb.max(root.get(FieldNameConstants.LOCATION_ID)),
            maxDispenseTime,
            cb.max(root.get(FieldNameConstants.ID)),
            cb.count(root.get(FieldNameConstants.ID)));
        dataCq.where(cb.and(predicates.toArray(new Predicate[0])));
        dataCq.groupBy(
            root.get(FieldNameConstants.ENCOUNTER_ID),
            root.get(FieldNameConstants.DEVICE_DEF_ID),
            root.get(FieldNameConstants.UNIT_CODE));
        dataCq.orderBy(cb.desc(maxDispenseTime));
        TypedQuery<Object[]> typed = this.entityManager.createQuery(dataCq);
        typed.setFirstResult(pageNo * pageSize);
        typed.setMaxResults(pageSize);
        List<Object[]> rows = typed.getResultList();
        List<ConsumableUsageDispenseMergedGroupQueryModel> models = new ArrayList<>();
        for (Object[] row : rows) {
            models.add(this.toConsumableUsageMergedGroupModel(row));
        }
        return models;
    }

    /**
     * 将 Criteria 聚合 Object[] 映射为合并分组读模型。
     *
     * @param row 聚合列：encounter / deviceDef / unit / patient / location / maxTime / maxId / lineCount
     * @return 读模型；列不足时返回空壳；netQuantity 固定 0（应用层回刷）
     */
    private ConsumableUsageDispenseMergedGroupQueryModel toConsumableUsageMergedGroupModel(Object[] row) {
        ConsumableUsageDispenseMergedGroupQueryModel model = new ConsumableUsageDispenseMergedGroupQueryModel();
        // 净量由应用层页内明细回载后汇总；此处占位 0，避免 SQL SUM(CASE) 炸连接
        model.setNetQuantity(BigDecimal.ZERO);
        if (row == null || row.length < 8) {
            model.setLineCount(0L);
            return model;
        }
        // Asm：聚合驱动列类型因方言而异（Long/Integer/BigInteger），禁硬转
        model.setEncounterId(this.toLongOrNull(row[0]));
        model.setDeviceDefId(this.toLongOrNull(row[1]));
        if (row[2] == null) {
            model.setUnitCode(null);
        } else {
            model.setUnitCode(String.valueOf(row[2]));
        }
        model.setPatientId(this.toLongOrNull(row[3]));
        model.setLocationId(this.toLongOrNull(row[4]));
        model.setMaxDispenseTime(this.toLocalDateTimeOrNull(row[5]));
        model.setSampleDispenseId(this.toLongOrNull(row[6]));
        Long lineCount = this.toLongOrNull(row[7]);
        if (lineCount == null) {
            model.setLineCount(0L);
        } else {
            model.setLineCount(lineCount);
        }
        return model;
    }

    /**
     * 聚合列安全转 Long（兼容 Number 子类）。
     *
     * @param value 驱动列
     * @return Long；无法转换时 null
     */
    private Long toLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * 聚合列安全转 LocalDateTime（兼容 Timestamp）。
     *
     * @param value 驱动列
     * @return LocalDateTime；无法转换时 null
     */
    private LocalDateTime toLocalDateTimeOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    /**
     * 耗材使用报表患者侧净汇总（按耗材房/品规/科室/患者；DM-WKF-007 §6.2.24）。
     *
     * <p>领域分桶支持 LOCATION / ITEM / DEPARTMENT / PATIENT；类型（CATEGORY）由应用层
     * 先 ITEM 汇总再按 DeviceDefinition.categoryCode 分桶。科室维须预写
     * {@code organizationIdByEncounterId}。缺省维 = LOCATION（对齐耗材房默认）。</p>
     *
     * @param query 筛选 + summaryDimension
     * @return 净汇总行
     */
    public List<ConsumableUsageDispenseSummaryRow> summarizeConsumableUsageDispense(
        ConsumableUsageReportPageQuery query) {
        return this.summarizeConsumableUsageDispenseWithLines(query).getSummaryRows();
    }

    /**
     * 耗材使用报表患者侧净数量汇总，并同次扫描产出明细事实行。
     *
     * <p>筛选与 {@link #summarizeConsumableUsageDispense} 一致；一次 {@code findAll} 同时分桶与映射明细行，
     * 供应用层汇总进售价复用，禁止再按页二次扫描同一窗。</p>
     *
     * @param query 筛选 + summaryDimension
     * @return 汇总行 + 同窗明细事实；无命中时两边均为空列表
     */
    public ConsumableUsageDispenseSummaryBundle summarizeConsumableUsageDispenseWithLines(
        ConsumableUsageReportPageQuery query) {
        ConsumableUsageDispenseSummaryBundle bundle = new ConsumableUsageDispenseSummaryBundle();
        if (query == null) {
            query = new ConsumableUsageReportPageQuery();
        }
        // Gate：空就诊 scope 拒查
        if (query.getResolvedEncounterScope() != null && query.getResolvedEncounterScope().isEmpty()) {
            return bundle;
        }
        // Pre：全量命中行（忽略分页键）后按维分桶；汇总不分页排序
        Specification<DeviceDispense> specification = this.buildConsumableUsageReportSpecification(query);
        List<DeviceDispense> rows = deviceDispenseRepository.findAll(specification);
        String dimension = StringUtils.trimToNull(query.getSummaryDimension());
        // Gate：未传维回落耗材房，与 init 默认 LOCATION 一致
        if (dimension == null) {
            dimension = ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION;
        }
        Map<Long, Long> orgByEncounter = query.getOrganizationIdByEncounterId();
        if (orgByEncounter == null) {
            orgByEncounter = Map.of();
        }
        // Asm：退药数量取负累加净量；同环映射明细行供定价
        Map<String, ConsumableUsageDispenseSummaryRow> bucketByKey = new LinkedHashMap<>();
        List<ConsumableUsageDispenseLineQueryModel> lineRows = new ArrayList<>(rows.size());
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
            lineRows.add(this.toConsumableUsageDispenseLineModel(row));
        }
        bundle.setSummaryRows(new ArrayList<>(bucketByKey.values()));
        bundle.setLineRows(lineRows);
        return bundle;
    }

    /**
     * 构建耗材使用报表规约：已发/已退（可按记录类型收窄）+ 时间窗 + 库位/品规/执行人/就诊 scope。
     *
     * @param query 筛选
     * @return JPA 规约
     */
    private Specification<DeviceDispense> buildConsumableUsageReportSpecification(
        ConsumableUsageReportPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = this.buildConsumableUsageReportPredicates(root, cb, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 组装耗材使用报表共用筛选谓词（明细分页 / 合并分组 / 汇总复用）。
     *
     * @param root  发放根
     * @param cb    CriteriaBuilder
     * @param query 筛选；null 视为空条件
     * @return 谓词列表（可空条件已跳过；调用方 {@code and}）
     */
    private List<Predicate> buildConsumableUsageReportPredicates(
        Root<DeviceDispense> root,
        CriteriaBuilder cb,
        ConsumableUsageReportPageQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        ConsumableUsageReportPageQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new ConsumableUsageReportPageQuery();
        }
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        // 报表事实：按记录类型收窄已发 / 已退（空或 ALL = 并集）
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM)
            .in(this.resolveConsumableUsageStatusCodes(safeQuery.getRecordTypeCode())));
        if (safeQuery.getBusinessTimeFrom() != null || safeQuery.getBusinessTimeTo() != null) {
            // 有时间窗时 dispense_time 必非空
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.DISPENSE_TIME)));
        }
        if (safeQuery.getBusinessTimeFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get(FieldNameConstants.DISPENSE_TIME), safeQuery.getBusinessTimeFrom()));
        }
        if (safeQuery.getBusinessTimeTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get(FieldNameConstants.DISPENSE_TIME), safeQuery.getBusinessTimeTo()));
        }
        if (safeQuery.getLocationId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), safeQuery.getLocationId()));
        } else if (safeQuery.getResolvedLocationIds() != null) {
            // 空列表 = 关键字无命中，强制无结果
            if (safeQuery.getResolvedLocationIds().isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(safeQuery.getResolvedLocationIds()));
            }
        }
        if (safeQuery.getResolvedDeviceDefIds() != null) {
            if (safeQuery.getResolvedDeviceDefIds().isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(
                    root.get(FieldNameConstants.DEVICE_DEF_ID).in(safeQuery.getResolvedDeviceDefIds()));
            }
        }
        // 执行人（发耗材人）精确筛
        if (safeQuery.getPerformerId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.PERFORMER_ID), safeQuery.getPerformerId()));
        }
        if (safeQuery.getResolvedEncounterScope() != null) {
            if (safeQuery.getResolvedEncounterScope().isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(
                    root.get(FieldNameConstants.ENCOUNTER_ID).in(safeQuery.getResolvedEncounterScope()));
            }
        }
        return predicates;
    }

    /**
     * 按记录类型解析耗材报表纳入的发放状态码。
     *
     * @param recordTypeCode ALL / DISPENSE / REFUND；空视为全部
     * @return 状态 code 列表（非空）
     */
    private List<String> resolveConsumableUsageStatusCodes(String recordTypeCode) {
        String normalized = StringUtils.trimToNull(recordTypeCode);
        List<String> codes = new ArrayList<>();
        // 仅退发：REFUNDED
        if (ConsumableUsageReportConstants.RECORD_TYPE_REFUND.equals(normalized)) {
            codes.add(DispenseStatus.REFUNDED.getCode());
            return codes;
        }
        // 仅发放：已完成 / 部分完成
        if (ConsumableUsageReportConstants.RECORD_TYPE_DISPENSE.equals(normalized)) {
            codes.add(DispenseStatus.COMPLETED.getCode());
            codes.add(DispenseStatus.PART_COMPLETED.getCode());
            return codes;
        }
        // 全部（含未知码回落）：已发 ∪ 已退
        codes.add(DispenseStatus.COMPLETED.getCode());
        codes.add(DispenseStatus.PART_COMPLETED.getCode());
        codes.add(DispenseStatus.REFUNDED.getCode());
        return codes;
    }

    /**
     * 映射耗材使用明细行。
     */
    private ConsumableUsageDispenseLineQueryModel toConsumableUsageDispenseLineModel(DeviceDispense row) {
        ConsumableUsageDispenseLineQueryModel model = new ConsumableUsageDispenseLineQueryModel();
        model.setDispenseId(row.getId());
        // Asm：退药行 based_on → 源发放；账单仍锚源单，供使用报表售价回挂
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(row.getBasedOnTable())
            && row.getBasedOnId() != null) {
            model.setBasedOnDispenseId(row.getBasedOnId());
        }
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
