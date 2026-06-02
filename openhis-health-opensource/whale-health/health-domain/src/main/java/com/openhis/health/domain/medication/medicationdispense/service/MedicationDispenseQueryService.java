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

package com.openhis.health.domain.medication.medicationdispense.service;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationdispense.queries.model.DispenseExeReserveRowModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseDetailQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseListQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationPendingRefundQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.repository.MedicationDispenseRepository;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.constants.DoctorStationOrderCatalogConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.NurseStationExecutionConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 药品发放事实读侧查询服务。
 *
 * <p>职责：按发放单、就诊、汇总单号等维度查询与状态门控（可否配药/发药/作废/退药）；只读不写库。</p>
 *
 * <p>与 {@code AS-OP-DOR-003} IF-DOR-11 对齐：提供同就诊候选库位上的发放预占行批量加载，供目录装配扣减可用量。</p>
 *
 */
@Service
@RequiredArgsConstructor
public class MedicationDispenseQueryService {

    /** 发放事实仓储：派生查询与批量 IN 加载。 */
    private final MedicationDispenseRepository medicationDispenseRepository;

    /**
     * 门诊/住院发药与退药患者队列：跨就诊 EXISTS 的分组分页（B.2；派生方法无法表达时使用）。
     */
    private final EntityManager entityManager;

    /**
     * 加载发放单详情。
     *
     * @param id 发放单标识
     * @return 发放单
     */
    public MedicationDispenseDetailQueryModel loadMedicationDispenseById(Long id) {
        return this.toDetailModel(this.mustGetMedicationDispense(id));
    }

    /**
     * 按药品医嘱申请列出关联发放单主键（签退清理费用锚点用）。
     *
     * @param medReqId 医嘱申请主键
     * @return 发放单 id 列表；{@code medReqId} 为空时返回空表
     */
    public List<Long> listMedicationDispenseIdsByMedicationRequest(Long medReqId) {
        List<Long> result = new ArrayList<>();
        if (medReqId == null) {
            return result;
        }
        // 一医嘱可对应多条发放（拆批/补发），签退清理需收集全部发放 id
        List<MedicationDispense> rows = medicationDispenseRepository.findByMedReqIdIn(List.of(medReqId));
        for (MedicationDispense row : rows) {
            result.add(row.getId());
        }
        return result;
    }

    /**
     * 医生站医嘱列表回显：对每个药品医嘱取「发放主键升序第一条」的库位。
     *
     * @param medReqIds 医嘱申请主键集合
     * @return 医嘱 id → 首选发放库位 id
     */
    public Map<Long, Long> mapMedicationRequestToPrimaryDispenseLocationId(Collection<Long> medReqIds) {
        Map<Long, Long> locationByMedReq = new HashMap<>();
        Map<Long, Long> minDispenseIdByMedReq = new HashMap<>();
        if (medReqIds == null || medReqIds.isEmpty()) {
            return locationByMedReq;
        }
        List<Long> distinctIds = new ArrayList<>();
        for (Long id : medReqIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return locationByMedReq;
        }
        List<MedicationDispense> rows = medicationDispenseRepository.findByMedReqIdIn(distinctIds);
        for (MedicationDispense row : rows) {
            // 无库位的发放行无法作为 FIFO 展示锚点
            if (row.getMedReqId() == null || row.getLocationId() == null) {
                continue;
            }
            // 同一医嘱多条发放时取主键最小的一条作为「首选」库位（与医生站列表口径一致）
            Long prevMin = minDispenseIdByMedReq.get(row.getMedReqId());
            if (prevMin == null || row.getId() < prevMin) {
                minDispenseIdByMedReq.put(row.getMedReqId(), row.getId());
                locationByMedReq.put(row.getMedReqId(), row.getLocationId());
            }
        }
        return locationByMedReq;
    }

    /**
     * 按就诊查询发放单列表。
     *
     * @param encounterId 就诊标识
     * @return 发放单集合
     */
    public List<MedicationDispenseListQueryModel> listMedicationDispensesByEncounter(Long encounterId) {
        List<MedicationDispense> dispensess = medicationDispenseRepository.findByEncounterId(encounterId);
        List<MedicationDispenseListQueryModel> result = new ArrayList<>();
        // 就诊维度下列出发放单头信息，供医生站或药房工作台
        for (MedicationDispense dispense : dispensess) {
            MedicationDispenseListQueryModel model = new MedicationDispenseListQueryModel();
            model.setId(dispense.getId());
            model.setBusNo(dispense.getBusNo());
            model.setStatusEnum(dispense.getStatusEnum());
            model.setMedReqId(dispense.getMedReqId());
            model.setMedicationId(dispense.getMedicationId());
            model.setQuantity(dispense.getQuantity());
            model.setUnitCode(dispense.getUnitCode());
            model.setLocationId(dispense.getLocationId());
            model.setPlannedDispenseTime(dispense.getPlannedDispenseTime());
            result.add(model);
        }
        return result;
    }

    /**
     * 按汇总单号查询发放明细。
     *
     * @param summaryNo 汇总单号
     * @return 发放明细集合
     */
    public List<MedicationDispenseSummaryItemQueryModel> listMedicationDispensesBySummaryNo(String summaryNo) {
        List<MedicationDispense> dispensess = medicationDispenseRepository.findBySummaryNo(summaryNo);
        List<MedicationDispenseSummaryItemQueryModel> result = new ArrayList<>();
        // 汇总单维度：仅映射发药工作台关心的状态与数量
        for (MedicationDispense dispense : dispensess) {
            MedicationDispenseSummaryItemQueryModel model = new MedicationDispenseSummaryItemQueryModel();
            model.setSummaryNo(dispense.getSummaryNo());
            model.setId(dispense.getId());
            model.setStatusEnum(dispense.getStatusEnum());
            model.setQuantity(dispense.getQuantity());
            result.add(model);
        }
        return result;
    }

    /**
     * 按就诊查询待退药发放单。
     *
     * @param encounterId 就诊标识
     * @return 待退药发放单集合
     */
    public List<MedicationPendingRefundQueryModel> listPendingRefundDispensesByEncounter(Long encounterId) {
        List<MedicationDispense> dispensess = medicationDispenseRepository.findByEncounterIdAndStatusEnum(
            encounterId, DispenseStatus.PENDING_REFUND);
        List<MedicationPendingRefundQueryModel> result = new ArrayList<>();
        // 仅 PENDING_REFUND：供窗口确认退药前清单
        for (MedicationDispense dispense : dispensess) {
            MedicationPendingRefundQueryModel model = new MedicationPendingRefundQueryModel();
            model.setId(dispense.getId());
            model.setStatusEnum(dispense.getStatusEnum());
            model.setMedicationId(dispense.getMedicationId());
            model.setDispenseQuantity(dispense.getDispenseQuantity());
            result.add(model);
        }
        return result;
    }

    /**
     * 门诊退药明细：按就诊与可选退药状态查询发放事实（与 IF-PHA-02-02 队列同源口径）。
     *
     * @param encounterId      就诊主键
     * @param refundStatusCode {@link DispenseStatus#getCode()}；空则返回待退/已退/已拒绝子集
     * @return 未删除且符合门诊退药类型的发放行
     */
    public List<MedicationDispense> listOutpatientReturnMedicationDispensesForDetail(
        Long encounterId,
        String refundStatusCode
    ) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        DispenseStatus statusFilter = null;
        if (StringUtils.isNotBlank(refundStatusCode)) {
            statusFilter = EnumUtil.fromCode(DispenseStatus.class, refundStatusCode.trim());
        }
        List<MedicationDispense> source;
        // 指定退药态时先下推 status 谓词缩小扫描集，否则拉取本就诊全部发放再内存过滤
        if (statusFilter != null) {
            source = this.medicationDispenseRepository.findByEncounterIdAndStatusEnum(encounterId, statusFilter);
        } else {
            source = this.medicationDispenseRepository.findByEncounterId(encounterId);
        }
        List<MedicationDispense> result = new ArrayList<>();
        for (MedicationDispense md : source) {
            if (md == null || Boolean.TRUE.equals(md.getIsDeleted())) {
                continue;
            }
            // 类型/状态二次过滤：与队列聚合口径解耦，明细可含已拒退
            if (!this.isOutpatientReturnMedicationDispenseForDetail(md, statusFilter)) {
                continue;
            }
            result.add(md);
        }
        return result;
    }

    /**
     * 门诊退药明细行是否纳入结果集。
     *
     * @param md           药品发放事实
     * @param statusFilter 调用方指定状态过滤；{@code null} 时按退药业务默认口径
     * @return 纳入明细列表时 {@code true}
     */
    private boolean isOutpatientReturnMedicationDispenseForDetail(MedicationDispense md, DispenseStatus statusFilter) {
        MedicationDispenseType t = md.getDispenseEnum();
        // 无发放类型无法判定退药场景
        if (t == null) {
            return false;
        }
        // 仅门诊/临时/退药类型参与门诊退药读侧
        if (!MedicationDispenseType.OUTPATIENT.equals(t)
            && !MedicationDispenseType.TEMPORARY.equals(t)
            && !MedicationDispenseType.REFUND.equals(t)) {
            return false;
        }
        DispenseStatus st = md.getStatusEnum();
        if (st == null) {
            return false;
        }
        // 调用方显式过滤：只保留指定状态
        if (statusFilter != null) {
            return statusFilter.equals(st);
        }
        // 退药类型发放单一律展示
        if (MedicationDispenseType.REFUND.equals(t)) {
            return true;
        }
        if (DispenseStatus.PENDING_REFUND.equals(st)) {
            return true;
        }
        // 已退或拒退仍可在明细中追溯
        return DispenseStatus.REFUNDED.equals(st) || DispenseStatus.DECLINED.equals(st);
    }

    /**
     * 批量聚合就诊维度退药展示状态（有待退优先，否则已退，否则空串）。
     *
     * <p>供退药患者队列装配，单次 IN 查询避免按就诊循环查库（A.9.1）。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → {@link DispenseStatus#getCode()}；无命中行时值为空串
     */
    public Map<Long, String> mapAggregatedRefundStatusCodeByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, String> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> dedupedSet = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                dedupedSet.add(encounterId);
            }
        }
        List<Long> deduped = new ArrayList<>(dedupedSet);
        if (deduped.isEmpty()) {
            return result;
        }
        // 单次 IN 拉取本页就诊全部发放，内存按就诊聚合
        List<MedicationDispense> rows = this.medicationDispenseRepository.findByEncounterIdIn(deduped);
        Map<Long, Boolean> anyPendingByEncounter = new HashMap<>();
        Map<Long, Boolean> anyRefundedByEncounter = new HashMap<>();
        for (MedicationDispense md : rows) {
            if (md == null || md.getEncounterId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(md.getIsDeleted())) {
                continue;
            }
            if (!this.isOutpatientReturnMedicationDispenseForQueueAggregation(md)) {
                continue;
            }
            Long encId = md.getEncounterId();
            DispenseStatus st = md.getStatusEnum();
            // 同一就诊下任一行待退即标记「有待退」
            if (DispenseStatus.PENDING_REFUND.equals(st)) {
                anyPendingByEncounter.put(encId, Boolean.TRUE);
            }
            if (DispenseStatus.REFUNDED.equals(st)) {
                anyRefundedByEncounter.put(encId, Boolean.TRUE);
            }
        }
        // 输出优先级：待退 > 已退 > 空（与队列列展示约定一致）
        for (Long encounterId : deduped) {
            if (Boolean.TRUE.equals(anyPendingByEncounter.get(encounterId))) {
                result.put(encounterId, DispenseStatus.PENDING_REFUND.getCode());
                continue;
            }
            if (Boolean.TRUE.equals(anyRefundedByEncounter.get(encounterId))) {
                result.put(encounterId, DispenseStatus.REFUNDED.getCode());
                continue;
            }
            // 该就诊无待退/已退发放：队列列展示为空（非 null）
            result.put(encounterId, GeneralSharedConstants.EMPTY_STRING);
        }
        return result;
    }

    /**
     * 退药患者队列聚合口径：门诊/临时/退药类型且状态为待退或已退。
     *
     * @param md 发放事实
     * @return 是否纳入就诊维度聚合
     */
    private boolean isOutpatientReturnMedicationDispenseForQueueAggregation(MedicationDispense md) {
        MedicationDispenseType t = md.getDispenseEnum();
        if (t == null) {
            return false;
        }
        if (!MedicationDispenseType.OUTPATIENT.equals(t)
            && !MedicationDispenseType.TEMPORARY.equals(t)
            && !MedicationDispenseType.REFUND.equals(t)) {
            return false;
        }
        DispenseStatus st = md.getStatusEnum();
        // 队列聚合只关心待退/已退，其它状态不参与就诊维度汇总
        if (st == null) {
            return false;
        }
        if (!DispenseStatus.PENDING_REFUND.equals(st) && !DispenseStatus.REFUNDED.equals(st)) {
            return false;
        }
        return true;
    }

    /**
     * 医嘱开立目录：同就诊、候选库位与目录药品定义交集下，处于草稿/待发链上的发放数量行（IF-DOR-11 预减口径）。
     *
     * @param encounterId               就诊主键
     * @param candidateLocationIds      候选库房库位 id 列表
     * @param medicationDefinitionIds   当前页药品定义 id 列表
     * @return 占用行；任一入参为空或集合空时返回空列表
     */
    public List<MedicationDispenseOrderCatalogOccupancyModel> listMedicationDispensesForOrderCatalogOccupancy(
        Long encounterId, List<Long> candidateLocationIds, List<Long> medicationDefinitionIds) {
        List<MedicationDispenseOrderCatalogOccupancyModel> result = new ArrayList<>();
        // 缺就诊或候选维度时无法界定「本单可扣减」范围，直接空结果避免全表扫描式装配。
        if (encounterId == null) {
            return result;
        }
        if (candidateLocationIds == null || candidateLocationIds.isEmpty()) {
            return result;
        }
        if (medicationDefinitionIds == null || medicationDefinitionIds.isEmpty()) {
            return result;
        }
        Specification<MedicationDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), encounterId));
            predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(candidateLocationIds));
            predicates.add(root.get(FieldNameConstants.MEDICATION_ID).in(medicationDefinitionIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        // 单次 IN 查询拉齐本页药品在候选库位上的预占行，满足 A.9.1 批量读口径。
        List<MedicationDispense> rows = medicationDispenseRepository.findAll(specification);
        for (MedicationDispense row : rows) {
            MedicationDispenseOrderCatalogOccupancyModel model = new MedicationDispenseOrderCatalogOccupancyModel();
            model.setMedicationId(row.getMedicationId());
            model.setLocationId(row.getLocationId());
            model.setQuantity(row.getQuantity());
            model.setUnitCode(row.getUnitCode());
            model.setStatusEnum(row.getStatusEnum());
            result.add(model);
        }
        return result;
    }

    /**
     * 护士站执行前：按药房+药品批量汇总全局待发预占（IF-IP-EXE-02 / BR-IP-EXE-014）。
     *
     * <p>状态口径同 {@link DoctorStationOrderCatalogConstants#DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG}；
     * <strong>不限就诊</strong>，统计该药房+药品下全部未完成发药闭环占用。可用库存由
     * {@link com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService#sumAvailableInventory}
     * 汇总；FIFO dry-run 与 Gate 编排在应用层完成。</p>
     *
     * @param locationIds   药房库位主键集合
     * @param medicationIds 药品定义主键集合
     * @return 按库位+药品聚合的预占行；入参为空时返回空列表
     */
    public List<DispenseExeReserveRowModel> listDispenseExeReserve(
        Collection<Long> locationIds, Collection<Long> medicationIds) {
        List<DispenseExeReserveRowModel> results = new ArrayList<>();
        // 无药房或药品维度则无法界定预占汇总范围
        if (locationIds == null || locationIds.isEmpty() || medicationIds == null || medicationIds.isEmpty()) {
            return results;
        }
        Specification<MedicationDispense> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationIds));
            predicates.add(root.get(FieldNameConstants.MEDICATION_ID).in(medicationIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                NurseStationExecutionConstants.DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // 内存按库位+药品累加待发数量（单次 findAll，满足 A.9.1）
        Map<Long, Map<Long, BigDecimal>> reserveByLocationMedication = new HashMap<>();
        List<MedicationDispense> pendingRows = medicationDispenseRepository.findAll(specification);
        for (MedicationDispense row : pendingRows) {
            Map<Long, BigDecimal> byMedication = reserveByLocationMedication.computeIfAbsent(
                row.getLocationId(), ignored -> new HashMap<>());
            BigDecimal current = byMedication.getOrDefault(row.getMedicationId(), BigDecimal.ZERO);
            if (row.getQuantity() != null) {
                current = current.add(row.getQuantity());
            }
            byMedication.put(row.getMedicationId(), current);
        }
        // 展平为护士站 Gate 可消费的 (locationId, medicationId, reserveQty) 行
        for (Map.Entry<Long, Map<Long, BigDecimal>> locationEntry : reserveByLocationMedication.entrySet()) {
            for (Map.Entry<Long, BigDecimal> medicationEntry : locationEntry.getValue().entrySet()) {
                DispenseExeReserveRowModel model = new DispenseExeReserveRowModel();
                model.setLocationId(locationEntry.getKey());
                model.setMedicationId(medicationEntry.getKey());
                model.setReserveQuantity(medicationEntry.getValue());
                results.add(model);
            }
        }
        return results;
    }

    /**
     * 按主键强制加载发放单。
     *
     * @param id 发放单标识
     * @return 发放单
     */
    public MedicationDispense mustGetMedicationDispense(Long id) {
        // 状态门控与详情读侧统一：缺失即业务异常，不返回 null
        return medicationDispenseRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_NOT_FOUND, "药品发放记录不存在"));
    }

    /**
     * 批量按主键加载发放聚合行（门诊发药等编排批量门禁；单次 IN 查询）。
     *
     * @param ids 发放单主键集合
     * @return 实体列表；空入参返回空表
     */
    public List<MedicationDispense> listMedicationDispensesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return medicationDispenseRepository.findByIdIn(ids);
    }

    /**
     * 校验是否允许配药。
     *
     * @param id 发放单标识
     * @return 当前处于配药中状态时为 true
     */
    public boolean validateDispensePreparable(Long id) {
        MedicationDispense dispense = this.mustGetMedicationDispense(id);
        // 仅「配药中」可继续配药操作
        return DispenseStatus.PREPARATION.equals(dispense.getStatusEnum());
    }

    /**
     * 校验是否允许发药。
     *
     * @param id 发放单标识
     * @return 已配药或已汇总待发放时为 true
     */
    public boolean validateDispensable(Long id) {
        MedicationDispense dispense = this.mustGetMedicationDispense(id);
        // 已配药、发药中或已汇总待窗口发药
        return DispenseStatus.PREPARED.equals(dispense.getStatusEnum())
            || DispenseStatus.IN_PROGRESS.equals(dispense.getStatusEnum())
            || DispenseStatus.SUMMARIZED.equals(dispense.getStatusEnum());
    }

    /**
     * 校验是否允许作废未发。
     *
     * @param id 发放单标识
     * @return 尚未完成发药闭环时为 true
     */
    public boolean validateDispenseCancellable(Long id) {
        MedicationDispense dispense = this.mustGetMedicationDispense(id);
        // 未完成发药闭环前允许作废未发
        return DispenseStatus.DRAFT.equals(dispense.getStatusEnum())
            || DispenseStatus.PREPARATION.equals(dispense.getStatusEnum())
            || DispenseStatus.PREPARED.equals(dispense.getStatusEnum())
            || DispenseStatus.IN_PROGRESS.equals(dispense.getStatusEnum());
    }

    /**
     * 校验是否允许退药。
     *
     * @param id 发放单标识
     * @return 已完成发药或处于待退药处理时为 true
     */
    public boolean validateRefundable(Long id) {
        MedicationDispense dispense = this.mustGetMedicationDispense(id);
        // 已发完或已进入退药流程
        return DispenseStatus.COMPLETED.equals(dispense.getStatusEnum())
            || DispenseStatus.PART_COMPLETED.equals(dispense.getStatusEnum())
            || DispenseStatus.PENDING_REFUND.equals(dispense.getStatusEnum());
    }


    /**
     * 批量解析发放单主键到药品申请主键（医生站费用锚点归并，AS-OP-DOR-003 IF-DOR-10）。
     *
     * @param dispenseIds 发放单主键集合
     * @return 发放单 id → 药品申请 id；入参空时返回空 Map
     */
    public Map<Long, Long> mapMedicationDispenseIdsToMedReqIds(Collection<Long> dispenseIds) {
        Map<Long, Long> map = new HashMap<>();
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return map;
        }
        List<MedicationDispense> rows = medicationDispenseRepository.findByIdIn(dispenseIds);
        for (MedicationDispense row : rows) {
            if (row.getMedReqId() != null) {
                map.put(row.getId(), row.getMedReqId());
            }
        }
        return map;
    }

    /**
     * 按药品申请主键集合批量加载发放单（签发后迁移费用草稿用，AS-OP-DOR-003 IF-DOR-15）。
     *
     * @param medReqIds 药品申请主键集合
     * @return 发放单实体列表
     */
    public List<MedicationDispense> listMedicationDispensesByMedReqIds(Collection<Long> medReqIds) {
        if (medReqIds == null || medReqIds.isEmpty()) {
            return List.of();
        }
        return medicationDispenseRepository.findByMedReqIdIn(medReqIds);
    }

    /**
     * 发放单详情读模型映射（关联医嘱与数量事实）。
     *
     * @param dispense 持久化发放聚合行
     * @return 供工作台/详情接口使用的查询模型
     */
    private MedicationDispenseDetailQueryModel toDetailModel(MedicationDispense dispense) {
        MedicationDispenseDetailQueryModel model = new MedicationDispenseDetailQueryModel();
        model.setId(dispense.getId());
        model.setStatusEnum(dispense.getStatusEnum());
        model.setMedReqId(dispense.getMedReqId());
        model.setQuantity(dispense.getQuantity());
        model.setDispenseQuantity(dispense.getDispenseQuantity());
        return model;
    }

    /**
     * 发药单分页（替代 MedicationDispenseSlipRMQueryService#pageMedicationDispenseSlip）。
     */
    public Page<MedicationDispenseSlipQueryModel> pageMedicationDispenseSlip(MedicationDispenseSlipPageQuery query) {
        int pageNo = 0;
        int pageSize = GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
        if (query != null && query.getPageNo() != null && query.getPageNo() > 0) {
            pageNo = query.getPageNo() - 1;
        }
        if (query != null && query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getPageSize();
        }
        PageRequest pageable = PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        Specification<MedicationDispense> specification = this.buildDispenseSlipSpecification(query);
        // 发药单列表：Specification 下推 DB 分页，再映射为 slip 读模型
        Page<MedicationDispense> page = medicationDispenseRepository.findAll(specification, pageable);
        List<MedicationDispenseSlipQueryModel> models = new ArrayList<>();
        for (MedicationDispense row : page.getContent()) {
            models.add(this.toSlipQueryModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 发药单列表（替代 MedicationDispenseSlipRMQueryService#listMedicationDispenseSlip）。
     */
    public List<MedicationDispenseSlipQueryModel> listMedicationDispenseSlip(MedicationDispenseSlipPageQuery query) {
        Specification<MedicationDispense> specification = this.buildDispenseSlipSpecification(query);
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        // 不分页导出/打印场景：仍下推 WHERE 到 DB
        List<MedicationDispense> rows = medicationDispenseRepository.findAll(specification, sort);
        List<MedicationDispenseSlipQueryModel> models = new ArrayList<>();
        for (MedicationDispense row : rows) {
            models.add(this.toSlipQueryModel(row));
        }
        return models;
    }

    /**
     * 组装药品发放单（slip）列表的 JPA 动态谓词（库位/就诊/患者/状态/关键字等下推 DB）。
     *
     * @param query 发放单分页查询入参；{@code null} 时仅软删与挂起态过滤
     * @return 供 {@link MedicationDispenseRepository#findAll(Specification, Sort)} 使用的规约
     */
    private Specification<MedicationDispense> buildDispenseSlipSpecification(MedicationDispenseSlipPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 基线：未删除且非挂起态（挂起单独业务流）
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.ON_HOLD));
            if (query != null) {
                if (query.getLocationId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
                }
                if (query.getEncounterId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
                }
                if (query.getPatientId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
                if (query.getMedicationId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.MEDICATION_ID), query.getMedicationId()));
                }
                if (query.getDispenseStatusEnumIn() != null && !query.getDispenseStatusEnumIn().isEmpty()) {
                    List<DispenseStatus> statusList = new ArrayList<>();
                    for (String code : query.getDispenseStatusEnumIn()) {
                        if (StringUtils.isBlank(code)) {
                            continue;
                        }
                        DispenseStatus st = EnumUtil.fromCode(DispenseStatus.class, code.trim());
                        if (st != null) {
                            statusList.add(st);
                        }
                    }
                    if (!statusList.isEmpty()) {
                        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusList));
                    }
                } else if (StringUtils.isNotBlank(query.getStatusEnum())) {
                    // 单状态筛选：与多选列表互斥，优先多选
                    DispenseStatus single = EnumUtil.fromCode(DispenseStatus.class, query.getStatusEnum().trim());
                    if (single != null) {
                        predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), single));
                    }
                }
                // 门诊发药单列表：仅看门诊/临时发放类型
                if (Boolean.TRUE.equals(query.getOutpatientMedicationOnly())) {
                    predicates.add(root.get(FieldNameConstants.DISPENSE_ENUM).in(List.of(
                        MedicationDispenseType.OUTPATIENT,
                        MedicationDispenseType.TEMPORARY)));
                }
                // 住院发药单列表：仅看住院/临时发放类型
                if (Boolean.TRUE.equals(query.getInpatientMedicationOnly())) {
                    predicates.add(root.get(FieldNameConstants.DISPENSE_ENUM).in(List.of(
                        MedicationDispenseType.INPATIENT,
                        MedicationDispenseType.TEMPORARY)));
                }
                if (query.getRequestStatusEnumIn() != null && !query.getRequestStatusEnumIn().isEmpty()) {
                    if (query.getResolvedMedReqIdsForRequestStatus() == null
                        || query.getResolvedMedReqIdsForRequestStatus().isEmpty()) {
                        predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
                    } else {
                        predicates.add(root.get(FieldNameConstants.MED_REQ_ID).in(query.getResolvedMedReqIdsForRequestStatus()));
                    }
                }
                if (query.getResolvedEncounterIdsForKeyword() != null) {
                    if (query.getResolvedEncounterIdsForKeyword().isEmpty()) {
                        predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
                    } else {
                        predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(query.getResolvedEncounterIdsForKeyword()));
                    }
                }
                if (StringUtils.isNotBlank(query.getLotNumber())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NUMBER), query.getLotNumber()));
                }
                if (StringUtils.isNotBlank(query.getSummaryNo())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.SUMMARY_NO), query.getSummaryNo()));
                }
                // 状态变更时间窗：用于发药工作台按操作时段筛选（非队列默认轴）
                if (query.getStartDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.STATUS_CHANGED_TIME), query.getStartDate()));
                }
                if (query.getEndDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.STATUS_CHANGED_TIME), query.getEndDate()));
                }
                // 已发药工作台：排除退药确认后仍停留在 COMPLETED 的正向锚点（历史数据兜底）
                if (this.slipQueryIncludesCompletedStatus(query)) {
                    predicates.add(cb.not(this.existsForwardDispenseSupersededByConfirmedRefund(root, criteriaQuery, cb)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 发药单列表是否按「已发药」态筛选（含多选中的 COMPLETED）。
     *
     * @param query 发放单分页入参
     * @return 含 COMPLETED 筛选时为 true
     */
    private boolean slipQueryIncludesCompletedStatus(MedicationDispenseSlipPageQuery query) {
        if (query == null) {
            return false;
        }
        if (StringUtils.isNotBlank(query.getStatusEnum())) {
            if (DispenseStatus.COMPLETED.getCode().equals(query.getStatusEnum().trim())) {
                return true;
            }
        }
        if (query.getDispenseStatusEnumIn() == null || query.getDispenseStatusEnumIn().isEmpty()) {
            return false;
        }
        for (String code : query.getDispenseStatusEnumIn()) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            if (DispenseStatus.COMPLETED.getCode().equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否存在已确认的退药发放行，其退药医嘱指向当前正向发放行的原药品医嘱。
     *
     * @param root 主查询发放根
     * @param cq   所属 CriteriaQuery
     * @param cb   CriteriaBuilder
     * @return EXISTS 谓词
     */
    private Predicate existsForwardDispenseSupersededByConfirmedRefund(
        Root<MedicationDispense> root,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb
    ) {
        Subquery<Long> sq = cq.subquery(Long.class);
        Root<MedicationDispense> refundDispense = sq.from(MedicationDispense.class);
        Root<MedicationRequest> refundReq = sq.from(MedicationRequest.class);
        sq.select(refundDispense.get(FieldNameConstants.ID));
        sq.where(cb.and(
            cb.isFalse(refundDispense.get(FieldNameConstants.IS_DELETED)),
            cb.equal(refundDispense.get(FieldNameConstants.DISPENSE_ENUM), MedicationDispenseType.REFUND),
            cb.equal(refundDispense.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.REFUNDED),
            cb.equal(refundReq.get(FieldNameConstants.ID), refundDispense.get(FieldNameConstants.MED_REQ_ID)),
            cb.isFalse(refundReq.get(FieldNameConstants.IS_DELETED)),
            cb.equal(refundReq.get(FieldNameConstants.REFUND_MEDICINE_ID), root.get(FieldNameConstants.MED_REQ_ID)),
            cb.notEqual(root.get(FieldNameConstants.DISPENSE_ENUM), MedicationDispenseType.REFUND),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.COMPLETED)));
        return cb.exists(sq);
    }

    /**
     * 发放实体 → 发放单列表读模型（不含跨表补全，由调用方批量预热）。
     *
     * @param dispense 药品发放实体
     * @return 发放单查询模型
     */
    private MedicationDispenseSlipQueryModel toSlipQueryModel(MedicationDispense dispense) {
        // 字段直映发放聚合，患者/药品名由应用层批量补全
        MedicationDispenseSlipQueryModel model = new MedicationDispenseSlipQueryModel();
        model.setDispenseId(dispense.getId());
        model.setBusNo(dispense.getBusNo());
        model.setMedReqId(dispense.getMedReqId());
        model.setStatusEnum(dispense.getStatusEnum());
        model.setNotPerformedReasonEnum(dispense.getNotPerformedReasonEnum());
        model.setStatusChangedTime(dispense.getStatusChangedTime());
        model.setDispenseEnum(dispense.getDispenseEnum());
        model.setMedicationId(dispense.getMedicationId());
        model.setPatientId(dispense.getPatientId());
        model.setEncounterId(dispense.getEncounterId());
        model.setLocationId(dispense.getLocationId());
        model.setQuantity(dispense.getQuantity());
        model.setUnitCode(dispense.getUnitCode());
        model.setDispenseQuantity(dispense.getDispenseQuantity());
        model.setLotNumber(dispense.getLotNumber());
        model.setTraceNo(dispense.getTraceNo());
        model.setPreparerId(dispense.getPreparerId());
        model.setPrepareTime(dispense.getPrepareTime());
        model.setDispenserId(dispense.getPractitionerId());
        model.setDispenseTime(dispense.getDispenseTime());
        model.setSummaryNo(dispense.getSummaryNo());
        return model;
    }

    /**
     * 门诊发药队列：分页返回「存在门诊药品发放事实（状态子集可收窄）」的就诊主键。
     *
     * <p>关键字过滤关联 {@code hisrm_outpatient_info}，避免全表扫描后在内存分页。</p>
     *
     * <p>不按 {@code status_changed_time} 时间窗筛选（该字段随配药/发药状态变更，不宜作队列收敛轴）。</p>
     *
     * @param query 查询入参；{@code null} 视为空条件对象；分页未传时回落第 1 页、默认页大小（{@link GeneralSharedConstants#DEFAULT_QUERY_PAGE_SIZE}）
     * @return 就诊主键页（按本就诊下命中发放行的最近 {@code created_at} 倒序）
     */
    public Page<Long> pageEncounterIdsForOutpatientPharmacyPending(OutpatientPharmacyPendingEncounterPageQuery query) {
        if (query == null) {
            query = new OutpatientPharmacyPendingEncounterPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = this.resolveQueuePageSize(query);
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<MedicationDispense> countRoot = countCq.from(MedicationDispense.class);
        Predicate queuePredicate = this.buildOutpatientPharmacyEncounterQueuePredicate(countRoot, countCq, cb, query);
        countCq.select(cb.countDistinct(countRoot.get(FieldNameConstants.ENCOUNTER_ID)));
        countCq.where(queuePredicate);
        Long totalRow = this.entityManager.createQuery(countCq).getSingleResult();
        long total = 0L;
        if (totalRow != null) {
            total = totalRow.longValue();
        }
        CriteriaQuery<Long> dataCq = cb.createQuery(Long.class);
        Root<MedicationDispense> md = dataCq.from(MedicationDispense.class);
        Predicate dataPredicate = this.buildOutpatientPharmacyEncounterQueuePredicate(md, dataCq, cb, query);
        dataCq.select(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.where(dataPredicate);
        dataCq.groupBy(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.orderBy(cb.desc(cb.max(md.get(FieldNameConstants.CREATED_AT))));
        TypedQuery<Long> typed = this.entityManager.createQuery(dataCq);
        typed.setFirstResult(pageNo * pageSize);
        typed.setMaxResults(pageSize);
        List<Long> encounterIds = typed.getResultList();
        PageRequest pageable = PageRequest.of(pageNo, pageSize);
        return new PageImpl<>(encounterIds, pageable, total);
    }

    /**
     * 住院发药队列：分页返回「存在药品发放事实且住院在院」的就诊主键（AS-IP-PHA-001 IF-IPP-02）。
     *
     * <p>关键字、在院状态、可选病区过滤关联 {@code hisrm_inpatient_info}，避免全表扫描后在内存分页。</p>
     * <p>不按 {@code status_changed_time} 时间窗筛选（该字段随配药/发药状态变更，不宜作队列收敛轴）。</p>
     *
     * @param query 查询入参；{@code null} 视为空条件对象
     * @return 就诊主键页（按本就诊下命中发放行的最近 {@code created_at} 倒序）
     */
    public Page<Long> pageEncounterIdsForInpatientPharmacyPending(InpatientPharmacyPendingEncounterPageQuery query) {
        if (query == null) {
            query = new InpatientPharmacyPendingEncounterPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = this.resolveQueuePageSize(query);
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<MedicationDispense> countRoot = countCq.from(MedicationDispense.class);
        Predicate queuePredicate = this.buildInpatientPharmacyEncounterQueuePredicate(countRoot, countCq, cb, query);
        countCq.select(cb.countDistinct(countRoot.get(FieldNameConstants.ENCOUNTER_ID)));
        countCq.where(queuePredicate);
        Long totalRow = this.entityManager.createQuery(countCq).getSingleResult();
        long total = 0L;
        if (totalRow != null) {
            total = totalRow.longValue();
        }
        CriteriaQuery<Long> dataCq = cb.createQuery(Long.class);
        Root<MedicationDispense> md = dataCq.from(MedicationDispense.class);
        Predicate dataPredicate = this.buildInpatientPharmacyEncounterQueuePredicate(md, dataCq, cb, query);
        dataCq.select(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.where(dataPredicate);
        dataCq.groupBy(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.orderBy(cb.desc(cb.max(md.get(FieldNameConstants.CREATED_AT))));
        TypedQuery<Long> typed = this.entityManager.createQuery(dataCq);
        typed.setFirstResult(pageNo * pageSize);
        typed.setMaxResults(pageSize);
        List<Long> encounterIds = typed.getResultList();
        PageRequest pageable = PageRequest.of(pageNo, pageSize);
        return new PageImpl<>(encounterIds, pageable, total);
    }

    /**
     * 门诊药房退药患者队列：分页返回「存在待退药或已退药药品发放事实」的就诊主键（AS-OP-PHA-002 IF-PHA-02-02）。
     *
     * <p>DOC-CONFLICT：本方法仅基于 {@code med_medication_dispense} 收敛就诊；纯耗材退药且无药品发放行的就诊需后续与
     * {@code wkf_device_dispense} 做 UNION 分页后再消除差异。</p>
     *
     * @param query 查询入参；{@code null} 视为空条件对象
     * @return 就诊主键页（按本就诊下命中发放行的最近 {@code status_changed_time} 倒序）
     */
    public Page<Long> pageEncounterIdsForOutpatientPharmacyReturn(PageReturnPatientQuery query) {
        if (query == null) {
            query = new PageReturnPatientQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = this.resolveQueuePageSize(query);
        if (pageSize > 100) {
            pageSize = 100;
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<MedicationDispense> countRoot = countCq.from(MedicationDispense.class);
        Predicate queuePredicate = this.buildPharmacyReturnPatientQueuePredicate(countRoot, countCq, cb, query);
        countCq.select(cb.countDistinct(countRoot.get(FieldNameConstants.ENCOUNTER_ID)));
        countCq.where(queuePredicate);
        Long totalRow = this.entityManager.createQuery(countCq).getSingleResult();
        long total = 0L;
        if (totalRow != null) {
            total = totalRow.longValue();
        }
        CriteriaQuery<Long> dataCq = cb.createQuery(Long.class);
        Root<MedicationDispense> md = dataCq.from(MedicationDispense.class);
        Predicate dataPredicate = this.buildPharmacyReturnPatientQueuePredicate(md, dataCq, cb, query);
        dataCq.select(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.where(dataPredicate);
        dataCq.groupBy(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.orderBy(cb.desc(cb.max(md.get(FieldNameConstants.STATUS_CHANGED_TIME))));
        TypedQuery<Long> typed = this.entityManager.createQuery(dataCq);
        typed.setFirstResult(pageNo * pageSize);
        typed.setMaxResults(pageSize);
        List<Long> encounterIds = typed.getResultList();
        PageRequest pageable = PageRequest.of(pageNo, pageSize);
        return new PageImpl<>(encounterIds, pageable, total);
    }

    private Predicate buildPharmacyReturnPatientQueuePredicate(
        Root<MedicationDispense> md,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        PageReturnPatientQuery query
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(md.get(FieldNameConstants.IS_DELETED)));
        List<Long> locationIds = query.getLocationIds();
        if (locationIds != null && !locationIds.isEmpty()) {
            predicates.add(md.get(FieldNameConstants.LOCATION_ID).in(locationIds));
        }
        List<DispenseStatus> returnStatuses = List.of(DispenseStatus.PENDING_REFUND, DispenseStatus.REFUNDED);
        predicates.add(md.get(FieldNameConstants.STATUS_ENUM).in(returnStatuses));
        List<MedicationDispenseType> outpatientTypes = List.of(MedicationDispenseType.OUTPATIENT, MedicationDispenseType.TEMPORARY, MedicationDispenseType.REFUND);
        predicates.add(md.get(FieldNameConstants.DISPENSE_ENUM).in(outpatientTypes));
        LocalDateTime start = query.getStartDate();
        LocalDateTime end = query.getEndDate();
        if (start != null) {
            predicates.add(cb.greaterThanOrEqualTo(md.get(FieldNameConstants.STATUS_CHANGED_TIME), start));
        }
        if (end != null) {
            predicates.add(cb.lessThanOrEqualTo(md.get(FieldNameConstants.STATUS_CHANGED_TIME), end));
        }
        if (query.getDepartmentId() != null) {
            predicates.add(this.existsOutpatientEncounterForDispense(
                md, cq, cb, query.getDepartmentId(), null, false));
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            predicates.add(this.existsOutpatientEncounterForDispense(
                md, cq, cb, null, pattern, true));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private Predicate buildOutpatientPharmacyEncounterQueuePredicate(
        Root<MedicationDispense> md,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        OutpatientPharmacyPendingEncounterPageQuery query
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(md.get(FieldNameConstants.IS_DELETED)));
        List<Long> locationIds = query.getLocationIds();
        if (locationIds != null && !locationIds.isEmpty()) {
            predicates.add(md.get(FieldNameConstants.LOCATION_ID).in(locationIds));
        }
        List<DispenseStatus> statuses = this.resolveDispenseStatusEnumInForPredicate(query.getDispenseStatusEnumIn());
        if (!statuses.isEmpty()) {
            predicates.add(md.get(FieldNameConstants.STATUS_ENUM).in(statuses));
        }
        // 已发药队列与发药单明细分页口径一致：排除已被确认退药收口的正向 COMPLETED 行（历史数据兜底）。
        if (this.dispenseStatusEnumInIncludesCompleted(query.getDispenseStatusEnumIn())) {
            predicates.add(cb.not(this.existsForwardDispenseSupersededByConfirmedRefund(md, cq, cb)));
        }
        List<MedicationDispenseType> outpatientTypes = List.of(MedicationDispenseType.OUTPATIENT, MedicationDispenseType.TEMPORARY);
        predicates.add(md.get(FieldNameConstants.DISPENSE_ENUM).in(outpatientTypes));
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            predicates.add(this.existsOutpatientEncounterForDispense(
                md, cq, cb, null, pattern, false));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * 发放状态多选是否包含「已发药」（COMPLETED），用于队列与 slip 列表统一退药收口过滤。
     *
     * @param dispenseStatusEnumIn 状态 code 列表
     * @return 含 COMPLETED 时为 true
     */
    private boolean dispenseStatusEnumInIncludesCompleted(List<String> dispenseStatusEnumIn) {
        if (dispenseStatusEnumIn == null || dispenseStatusEnumIn.isEmpty()) {
            return false;
        }
        for (String code : dispenseStatusEnumIn) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            if (DispenseStatus.COMPLETED.getCode().equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    private Predicate buildInpatientPharmacyEncounterQueuePredicate(
        Root<MedicationDispense> md,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        InpatientPharmacyPendingEncounterPageQuery query
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(md.get(FieldNameConstants.IS_DELETED)));
        List<Long> locationIds = query.getLocationIds();
        if (locationIds != null && !locationIds.isEmpty()) {
            predicates.add(md.get(FieldNameConstants.LOCATION_ID).in(locationIds));
        }
        List<DispenseStatus> statuses = this.resolveDispenseStatusEnumInForPredicate(query.getDispenseStatusEnumIn());
        if (!statuses.isEmpty()) {
            predicates.add(md.get(FieldNameConstants.STATUS_ENUM).in(statuses));
        }
        List<MedicationDispenseType> inpatientTypes = List.of(
            MedicationDispenseType.INPATIENT, MedicationDispenseType.TEMPORARY);
        predicates.add(md.get(FieldNameConstants.DISPENSE_ENUM).in(inpatientTypes));
        predicates.add(this.buildInpatientEncounterExistsForPharmacyQueue(md, cq, cb, query));
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private Predicate buildInpatientEncounterExistsForPharmacyQueue(
        Root<MedicationDispense> md,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        InpatientPharmacyPendingEncounterPageQuery query
    ) {
        Subquery<Long> sq = cq.subquery(Long.class);
        Root<Encounter> encounterRoot = sq.from(Encounter.class);
        sq.select(cb.literal(1L));
        List<Predicate> subPredicates = new ArrayList<>();
        subPredicates.add(cb.equal(encounterRoot.get(FieldNameConstants.ID), md.get(FieldNameConstants.ENCOUNTER_ID)));
        subPredicates.add(cb.isFalse(encounterRoot.get(FieldNameConstants.IS_DELETED)));
        subPredicates.add(cb.equal(encounterRoot.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()));
        subPredicates.add(encounterRoot.get(FieldNameConstants.STATUS_ENUM).in(
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode()));
        if (query.getWardId() != null) {
            subPredicates.add(this.inpatientWardScope(encounterRoot, cq, cb, query.getWardId()));
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            Predicate busNoLike = cb.like(cb.lower(encounterRoot.get(FieldNameConstants.BUS_NO)), pattern);
            subPredicates.add(cb.or(
                busNoLike,
                this.existsInpatientPatientKeyword(encounterRoot, cb, cq, pattern),
                this.existsActiveBedNameLike(encounterRoot, cb, cq, pattern)));
        }
        sq.where(cb.and(subPredicates.toArray(new Predicate[0])));
        return cb.exists(sq);
    }

    private Predicate existsOutpatientEncounterForDispense(
        Root<MedicationDispense> md,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        Long organizationId,
        String keywordPattern,
        boolean includeIdCardInKeyword) {
        Subquery<Long> sq = cq.subquery(Long.class);
        Root<Encounter> encounterRoot = sq.from(Encounter.class);
        sq.select(cb.literal(1L));
        List<Predicate> subPredicates = new ArrayList<>();
        subPredicates.add(cb.equal(encounterRoot.get(FieldNameConstants.ID), md.get(FieldNameConstants.ENCOUNTER_ID)));
        subPredicates.add(cb.isFalse(encounterRoot.get(FieldNameConstants.IS_DELETED)));
        subPredicates.add(encounterRoot.get(FieldNameConstants.CLASS_ENUM).in(
            EncounterClass.AMB.getCode(),
            EncounterClass.EMER.getCode()));
        if (organizationId != null) {
            subPredicates.add(cb.equal(encounterRoot.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
        }
        if (StringUtils.isNotBlank(keywordPattern)) {
            Predicate busNoLike = cb.like(cb.lower(encounterRoot.get(FieldNameConstants.BUS_NO)), keywordPattern);
            List<Predicate> keywordParts = new ArrayList<>();
            keywordParts.add(busNoLike);
            keywordParts.add(this.existsOutpatientPatientKeyword(
                encounterRoot, cb, cq, keywordPattern, includeIdCardInKeyword));
            subPredicates.add(cb.or(keywordParts.toArray(new Predicate[0])));
        }
        sq.where(cb.and(subPredicates.toArray(new Predicate[0])));
        return cb.exists(sq);
    }

    private Predicate existsOutpatientPatientKeyword(
        Root<Encounter> encounterRoot,
        CriteriaBuilder cb,
        CriteriaQuery<?> owningQuery,
        String pattern,
        boolean includeIdCard) {
        Subquery<Long> patientSq = owningQuery.subquery(Long.class);
        Root<Patient> patientRoot = patientSq.from(Patient.class);
        patientSq.select(patientRoot.get(FieldNameConstants.ID));
        List<Predicate> patientPredicates = new ArrayList<>();
        patientPredicates.add(cb.equal(patientRoot.get(FieldNameConstants.ID), encounterRoot.get(FieldNameConstants.PATIENT_ID)));
        patientPredicates.add(cb.isFalse(patientRoot.get(FieldNameConstants.IS_DELETED)));
        List<Predicate> textPredicates = new ArrayList<>();
        textPredicates.add(cb.like(cb.lower(patientRoot.get(FieldNameConstants.NAME)), pattern));
        textPredicates.add(cb.like(
            cb.lower(cb.coalesce(patientRoot.get(FieldNameConstants.PY_STR), "")),
            pattern));
        textPredicates.add(cb.like(
            cb.lower(cb.coalesce(patientRoot.get(FieldNameConstants.WB_STR), "")),
            pattern));
        if (includeIdCard) {
            textPredicates.add(cb.like(
                cb.lower(cb.coalesce(patientRoot.get(FieldNameConstants.ID_CARD), "")),
                pattern));
        }
        patientPredicates.add(cb.or(textPredicates.toArray(new Predicate[0])));
        patientSq.where(cb.and(patientPredicates.toArray(new Predicate[0])));
        return cb.exists(patientSq);
    }

    private Predicate existsInpatientPatientKeyword(
        Root<Encounter> encounterRoot,
        CriteriaBuilder cb,
        CriteriaQuery<?> owningQuery,
        String pattern) {
        return this.existsOutpatientPatientKeyword(encounterRoot, cb, owningQuery, pattern, false);
    }

    private Predicate existsActiveBedNameLike(
        Root<Encounter> encounterRoot,
        CriteriaBuilder cb,
        CriteriaQuery<?> owningQuery,
        String pattern) {
        Subquery<Long> sq = owningQuery.subquery(Long.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        Root<Location> locRoot = sq.from(Location.class);
        sq.select(elRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), encounterRoot.get(FieldNameConstants.ID)),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.LOCATION_ID), locRoot.get(FieldNameConstants.ID)),
            cb.isFalse(locRoot.get(FieldNameConstants.IS_DELETED)),
            cb.like(cb.lower(locRoot.get(FieldNameConstants.NAME)), pattern)));
        return cb.exists(sq);
    }

    private Predicate inpatientWardScope(
        Root<Encounter> encounterRoot,
        CriteriaQuery<?> owningQuery,
        CriteriaBuilder cb,
        Long wardId) {
        Subquery<Long> wardSq = owningQuery.subquery(Long.class);
        Root<EncounterLocation> wardEl = wardSq.from(EncounterLocation.class);
        wardSq.select(wardEl.get(FieldNameConstants.ENCOUNTER_ID));
        wardSq.where(cb.and(
            cb.equal(wardEl.get(FieldNameConstants.ENCOUNTER_ID), encounterRoot.get(FieldNameConstants.ID)),
            cb.equal(wardEl.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(wardEl.get(FieldNameConstants.FORM_ENUM), LocationForm.WARD.getCode()),
            cb.equal(wardEl.get(FieldNameConstants.LOCATION_ID), wardId)));

        Subquery<Long> bedSq = owningQuery.subquery(Long.class);
        Root<EncounterLocation> bedEl = bedSq.from(EncounterLocation.class);
        Root<Location> bedLoc = bedSq.from(Location.class);
        Root<Location> roomLoc = bedSq.from(Location.class);
        bedSq.select(bedEl.get(FieldNameConstants.ENCOUNTER_ID));
        bedSq.where(cb.and(
            cb.equal(bedEl.get(FieldNameConstants.ENCOUNTER_ID), encounterRoot.get(FieldNameConstants.ID)),
            cb.equal(bedEl.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(bedEl.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode()),
            cb.equal(bedEl.get(FieldNameConstants.LOCATION_ID), bedLoc.get(FieldNameConstants.ID)),
            cb.equal(bedLoc.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode()),
            cb.equal(roomLoc.get(FieldNameConstants.ID), bedLoc.get(FieldNameConstants.PARENT_ID)),
            cb.equal(roomLoc.get(FieldNameConstants.PARENT_ID), wardId)));
        return cb.or(cb.exists(wardSq), cb.exists(bedSq));
    }

    private int resolveQueuePageSize(AbstractPageQuery query) {
        if (query.getPageSize() != null && query.getPageSize() > 0) {
            return query.getBoundedPageSize();
        }
        return GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
    }

    private List<DispenseStatus> resolveDispenseStatusEnumInForPredicate(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<DispenseStatus> ordered = new LinkedHashSet<>();
        for (String raw : codes) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            DispenseStatus parsed = EnumUtil.fromCode(DispenseStatus.class, raw.trim());
            if (parsed != null) {
                ordered.add(parsed);
            }
        }
        return new ArrayList<>(ordered);
    }

}

