/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.service;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.DispenseExeReserveRowModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseDetailQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseListQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationPendingRefundQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.EncounterPendingTraceAssignmentQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.PatientTraceAssignmentAggregateLineQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.PatientTraceAssignmentDispenseLineQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.SummaryDispenseGateQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyReturnEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.EncountersPendingTraceAssignmentPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispensePickupPendingPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.repository.MedicationDispenseRepository;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.constants.DoctorStationOrderCatalogConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.NurseStationExecutionConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.utils.EnumUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import com.openhis.whale.core.application.util.StringUtils;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 药品发放事实读侧查询服务。
 *
 * <p>职责：按发放单、就诊、汇总单号等维度查询与状态门控（可否配药/发药/作废/退药）；只读不写库。</p>
 *
 * <p>与 {@code AS-OP-DOR-003} IF-DOR-11 对齐：提供同就诊候选库位上的发放预占行批量加载，供目录装配扣减可用量。</p>
 * <p>聚合边界：仅本聚合 {@link MedicationDispenseRepository}；{@link EntityManager} 仅用于跨就诊 EXISTS 分组分页。</p>
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
     * 长期嘱自然月累计：汇总指定医嘱在锚点所在自然月内未软删发放行的 {@code quantity} 合计（GAP-EXE-010）。
     *
     * @param medReqId   药品医嘱主键
     * @param anchorTime 计划执行时刻（用于确定自然月窗口）
     * @return 数量合计；无记录时返回 0
     */
    public BigDecimal sumActiveDispenseQuantityByMedReqIdInNaturalMonth(Long medReqId, LocalDateTime anchorTime) {
        if (medReqId == null || anchorTime == null) {
            return BigDecimal.ZERO;
        }
        LocalDateTime monthStart = anchorTime.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = anchorTime.toLocalDate()
            .withDayOfMonth(anchorTime.toLocalDate().lengthOfMonth())
            .atTime(23, 59, 59);
        Specification<MedicationDispense> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.MED_REQ_ID), medReqId));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), monthStart));
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), monthEnd));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationDispense> rows = medicationDispenseRepository.findAll(specification);
        BigDecimal total = BigDecimal.ZERO;
        for (MedicationDispense row : rows) {
            if (row != null && row.getQuantity() != null) {
                total = total.add(row.getQuantity());
            }
        }
        return total;
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
     * 退号门禁读侧（BR-REG-015）：加载就诊下未软删的药品发放实体。
     *
     * @param encounterId 就诊主键
     * @return 发放行列表；无记录时返回空表
     */
    public List<MedicationDispense> listUndeletedMedicationDispensesByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<MedicationDispense> rows = medicationDispenseRepository.findByEncounterId(encounterId);
        List<MedicationDispense> result = new ArrayList<>();
        for (MedicationDispense row : rows) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                result.add(row);
            }
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
        // 汇总单维度：映射核发核对、扣库与明细展示所需字段
        for (MedicationDispense dispense : dispensess) {
            MedicationDispenseSummaryItemQueryModel model = new MedicationDispenseSummaryItemQueryModel();
            model.setSummaryNo(dispense.getSummaryNo());
            model.setId(dispense.getId());
            model.setStatusEnum(dispense.getStatusEnum());
            model.setQuantity(dispense.getQuantity());
            model.setMedicationId(dispense.getMedicationId());
            model.setLocationId(dispense.getLocationId());
            model.setLotNumber(dispense.getLotNumber());
            model.setUnitCode(dispense.getUnitCode());
            model.setEncounterId(dispense.getEncounterId());
            model.setPatientId(dispense.getPatientId());
            model.setTraceNo(dispense.getTraceNo());
            result.add(model);
        }
        return result;
    }

    /**
     * 住院汇总发药列表：返回同号发放行<strong>全部</strong>处于指定态的 {@code summary_no} 集合（已发药仅以发放表为准）。
     *
     * @param requiredStatus   要求的一致态（待发 {@link DispenseStatus#SUMMARIZED} / 已发 {@link DispenseStatus#COMPLETED}）
     * @param locationIds      可选药房收窄；空则不拼 location 谓词
     * @return 满足一致态的汇总单号列表
     */
    public List<String> listSummaryNosWhereAllDispenseRowsHaveStatus(
        DispenseStatus requiredStatus,
        List<Long> locationIds) {
        if (requiredStatus == null) {
            return List.of();
        }
        List<MedicationDispense> scopeRows = this.listSummaryPathDispenseRowsForLocationFilter(locationIds);
        Map<String, List<DispenseStatus>> statusesBySummaryNo = new LinkedHashMap<>();
        for (MedicationDispense row : scopeRows) {
            if (row == null || StringUtils.isEmpty(row.getSummaryNo())) {
                continue;
            }
            String summaryNo = row.getSummaryNo().trim();
            List<DispenseStatus> bucket = statusesBySummaryNo.get(summaryNo);
            if (bucket == null) {
                bucket = new ArrayList<>();
                statusesBySummaryNo.put(summaryNo, bucket);
            }
            bucket.add(row.getStatusEnum());
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<DispenseStatus>> entry : statusesBySummaryNo.entrySet()) {
            List<DispenseStatus> statuses = entry.getValue();
            if (statuses == null || statuses.isEmpty()) {
                continue;
            }
            boolean allMatch = true;
            for (DispenseStatus status : statuses) {
                if (!requiredStatus.equals(status)) {
                    allMatch = false;
                    break;
                }
            }
            // 同号全部行处于目标态才纳入（部分 SUMMARIZED + 部分 COMPLETED 的单号排除）
            if (allMatch) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 批量解析汇总单号在发放表上的门控快照（是否已发药、最大发药时刻）。
     *
     * @param summaryNos 汇总单号集合
     * @return summaryNo → 门控读模型
     */
    public Map<String, SummaryDispenseGateQueryModel> indexSummaryDispenseGateBySummaryNos(Collection<String> summaryNos) {
        Map<String, SummaryDispenseGateQueryModel> result = new HashMap<>();
        if (summaryNos == null || summaryNos.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        for (String summaryNo : summaryNos) {
            if (StringUtils.isNotEmpty(summaryNo)) {
                dedupe.add(summaryNo.trim());
            }
        }
        if (dedupe.isEmpty()) {
            return result;
        }
        List<MedicationDispense> rows = medicationDispenseRepository.findBySummaryNoIn(dedupe);
        Map<String, List<MedicationDispense>> rowsBySummaryNo = new LinkedHashMap<>();
        for (MedicationDispense row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted()) || StringUtils.isEmpty(row.getSummaryNo())) {
                continue;
            }
            String summaryNo = row.getSummaryNo().trim();
            rowsBySummaryNo.computeIfAbsent(summaryNo, key -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<MedicationDispense>> entry : rowsBySummaryNo.entrySet()) {
            SummaryDispenseGateQueryModel gate = new SummaryDispenseGateQueryModel();
            gate.setSummaryNo(entry.getKey());
            boolean allCompleted = true;
            boolean allSummarized = true;
            LocalDateTime maxDispenseTime = null;
            for (MedicationDispense row : entry.getValue()) {
                DispenseStatus status = row.getStatusEnum();
                if (!DispenseStatus.COMPLETED.equals(status)) {
                    allCompleted = false;
                }
                if (!DispenseStatus.SUMMARIZED.equals(status)) {
                    allSummarized = false;
                }
                if (row.getDispenseTime() != null) {
                    if (maxDispenseTime == null || row.getDispenseTime().isAfter(maxDispenseTime)) {
                        maxDispenseTime = row.getDispenseTime();
                    }
                }
            }
            gate.setAllCompleted(allCompleted);
            gate.setAllSummarized(allSummarized);
            // 已发药 Tab 展示时刻：取同号发放行最大 dispense_time
            gate.setMaxDispenseTime(maxDispenseTime);
            result.put(entry.getKey(), gate);
        }
        return result;
    }

    /**
     * 汇总路径发放行基线读：{@code summary_no} 非空、未软删，可选按药房收窄。
     */
    private List<MedicationDispense> listSummaryPathDispenseRowsForLocationFilter(List<Long> locationIds) {
        Specification<MedicationDispense> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.SUMMARY_NO)));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.SUMMARY_NO), ""));
            if (locationIds != null && !locationIds.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return medicationDispenseRepository.findAll(specification);
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
     * @param locationIds      管辖库房收窄；非空时仅保留 {@code location_id} 命中行
     * @return 未删除且符合门诊退药类型的发放行
     */
    public List<MedicationDispense> listOutpatientReturnMedicationDispensesForDetail(
        Long encounterId,
        String refundStatusCode,
        List<Long> locationIds
    ) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        DispenseStatus statusFilter = null;
        if (StringUtils.isNotEmpty(refundStatusCode)) {
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
            if (!this.matchesManagedLocationScope(md, locationIds)) {
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
     * 住院退药明细：按就诊与可选退药状态查询发放事实（AS-IP-PHA-003 IF-IPR-03）。
     *
     * @param encounterId      就诊主键
     * @param refundStatusCode {@link DispenseStatus#getCode()}；空则返回待退/已退子集（不含拒退）
     * @param locationIds      管辖库房收窄；非空时仅保留 {@code location_id} 命中行
     * @return 未删除且符合住院退药类型的发放行
     */
    public List<MedicationDispense> listInpatientReturnMedicationDispensesForDetail(
        Long encounterId,
        String refundStatusCode,
        List<Long> locationIds
    ) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        DispenseStatus statusFilter = null;
        if (StringUtils.isNotEmpty(refundStatusCode)) {
            statusFilter = EnumUtil.fromCode(DispenseStatus.class, refundStatusCode.trim());
        }
        List<MedicationDispense> source;
        // 指定退药态时先下推 status 谓词，否则拉取本就诊全部发放再内存过滤
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
            if (!this.matchesManagedLocationScope(md, locationIds)) {
                continue;
            }
            if (!this.isInpatientReturnMedicationDispenseForDetail(md, statusFilter)) {
                continue;
            }
            result.add(md);
        }
        return result;
    }

    /**
     * 发放行是否落在管辖库房集合内；集合为空表示不过滤。
     *
     * @param dispense    发放事实
     * @param locationIds 管辖库位主键集合
     * @return 无收窄或命中时为 true
     */
    private boolean matchesManagedLocationScope(MedicationDispense dispense, List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return true;
        }
        if (dispense == null || dispense.getLocationId() == null) {
            return false;
        }
        return locationIds.contains(dispense.getLocationId());
    }

    /**
     * 住院退药明细行是否纳入结果集。
     *
     * @param md           药品发放事实
     * @param statusFilter 调用方指定状态过滤；{@code null} 时按住院退药默认口径
     * @return 纳入明细列表时 {@code true}
     */
    private boolean isInpatientReturnMedicationDispenseForDetail(MedicationDispense md, DispenseStatus statusFilter) {
        MedicationDispenseType t = md.getDispenseEnum();
        if (t == null) {
            return false;
        }
        // 住院/临时/退药类型参与住院退药读侧
        if (!MedicationDispenseType.INPATIENT.equals(t)
            && !MedicationDispenseType.TEMPORARY.equals(t)
            && !MedicationDispenseType.REFUND.equals(t)) {
            return false;
        }
        DispenseStatus st = md.getStatusEnum();
        if (st == null) {
            return false;
        }
        if (statusFilter != null) {
            return statusFilter.equals(st);
        }
        if (MedicationDispenseType.REFUND.equals(t)) {
            return true;
        }
        if (DispenseStatus.PENDING_REFUND.equals(st)) {
            return true;
        }
        return DispenseStatus.REFUNDED.equals(st);
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
     * 批量聚合住院就诊维度退药展示状态（有待退优先，否则已退，否则空串）。
     *
     * <p>供住院退药患者队列装配（AS-IP-PHA-003 IF-IPR-02）。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → {@link DispenseStatus#getCode()}；无命中行时值为空串
     */
    public Map<Long, String> mapAggregatedInpatientRefundStatusCodeByEncounterIds(Collection<Long> encounterIds) {
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
            if (!this.isInpatientReturnMedicationDispenseForQueueAggregation(md)) {
                continue;
            }
            Long encId = md.getEncounterId();
            DispenseStatus st = md.getStatusEnum();
            if (DispenseStatus.PENDING_REFUND.equals(st)) {
                anyPendingByEncounter.put(encId, Boolean.TRUE);
            }
            if (DispenseStatus.REFUNDED.equals(st)) {
                anyRefundedByEncounter.put(encId, Boolean.TRUE);
            }
        }
        for (Long encounterId : deduped) {
            if (Boolean.TRUE.equals(anyPendingByEncounter.get(encounterId))) {
                result.put(encounterId, DispenseStatus.PENDING_REFUND.getCode());
                continue;
            }
            if (Boolean.TRUE.equals(anyRefundedByEncounter.get(encounterId))) {
                result.put(encounterId, DispenseStatus.REFUNDED.getCode());
                continue;
            }
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
     * 住院退药患者队列聚合口径：住院/临时/退药类型且状态为待退或已退。
     *
     * @param md 发放事实
     * @return 是否纳入就诊维度聚合
     */
    private boolean isInpatientReturnMedicationDispenseForQueueAggregation(MedicationDispense md) {
        MedicationDispenseType t = md.getDispenseEnum();
        if (t == null) {
            return false;
        }
        if (!MedicationDispenseType.INPATIENT.equals(t)
            && !MedicationDispenseType.TEMPORARY.equals(t)
            && !MedicationDispenseType.REFUND.equals(t)) {
            return false;
        }
        DispenseStatus st = md.getStatusEnum();
        if (st == null) {
            return false;
        }
        if (!DispenseStatus.PENDING_REFUND.equals(st) && !DispenseStatus.REFUNDED.equals(st)) {
            return false;
        }
        return true;
    }

    /**
     * 医嘱开立目录：候选库位与目录药品定义交集下，处于草稿/待发且未过 {@code limitTime} 的发放数量行（IF-DOR-11 预减口径，不限就诊）。
     *
     * @param candidateLocationIds      候选库房库位 id 列表
     * @param medicationDefinitionIds   当前页药品定义 id 列表
     * @return 占用行；候选库位或定义集合为空时返回空列表
     */
    public List<MedicationDispenseOrderCatalogOccupancyModel> listMedicationDispensesForOrderCatalogOccupancy(
        List<Long> candidateLocationIds, List<Long> medicationDefinitionIds) {
        List<MedicationDispenseOrderCatalogOccupancyModel> result = new ArrayList<>();
        if (candidateLocationIds == null || candidateLocationIds.isEmpty()) {
            return result;
        }
        if (medicationDefinitionIds == null || medicationDefinitionIds.isEmpty()) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        Specification<MedicationDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(candidateLocationIds));
            predicates.add(root.get(FieldNameConstants.MEDICATION_ID).in(medicationDefinitionIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG));
            predicates.add(cb.greaterThan(root.get(FieldNameConstants.LIMIT_TIME), now));
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
     * 核发/配药 Gate：按库房+药品汇总待发预占（拆零单位），可排除当前发放行（GAP-EXE-003）。
     */
    public BigDecimal sumDispenseExeReserveQuantityInSplitUnit(
        Long locationId,
        Long medicationId,
        MedicationProductUnitConversionSource productDefinition,
        Long excludeDispenseId) {
        if (locationId == null || medicationId == null) {
            return BigDecimal.ZERO;
        }
        Specification<MedicationDispense> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), locationId));
            predicates.add(cb.equal(root.get(FieldNameConstants.MEDICATION_ID), medicationId));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                NurseStationExecutionConstants.DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE));
            if (excludeDispenseId != null) {
                predicates.add(cb.notEqual(root.get(FieldNameConstants.ID), excludeDispenseId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        BigDecimal reserve = BigDecimal.ZERO;
        for (MedicationDispense row : medicationDispenseRepository.findAll(specification)) {
            if (row == null || row.getQuantity() == null) {
                continue;
            }
            BigDecimal splitQty = com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter
                .medicationQuantityToSplitUnit(productDefinition, row.getUnitCode(), row.getQuantity());
            reserve = reserve.add(splitQty);
        }
        return reserve;
    }

    /**
     * 按主键强制加载发放单。
     *
     * @param id 发放单标识
     * @return 发放单
     */
    public MedicationDispense mustGetMedicationDispense(Long id) {
        // 状态门控与详情读侧统一：缺失或已软删即业务异常，不返回 null（GAP-STALE-003）
        MedicationDispense dispense = medicationDispenseRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_NOT_FOUND, "药品发放记录不存在"));
        dispense.assertNotDeleted();
        return dispense;
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
        List<MedicationDispense> rows = medicationDispenseRepository.findByIdIn(ids);
        List<MedicationDispense> result = new ArrayList<>();
        for (MedicationDispense row : rows) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 校验是否允许配药。
     *
     * @param id 发放单标识
     * @return 当前处于配药中状态时为 true
     */
    public boolean validateDispensePreparable(Long id) {
        MedicationDispense dispense = this.findById(id);
        if (dispense == null) {
            return false;
        }
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
        MedicationDispense dispense = this.findById(id);
        if (dispense == null) {
            return false;
        }
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
        MedicationDispense dispense = this.findById(id);
        if (dispense == null) {
            return false;
        }
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
        MedicationDispense dispense = this.findById(id);
        if (dispense == null) {
            return false;
        }
        // 已发完或已进入退药流程
        return DispenseStatus.COMPLETED.equals(dispense.getStatusEnum())
            || DispenseStatus.PART_COMPLETED.equals(dispense.getStatusEnum())
            || DispenseStatus.PENDING_REFUND.equals(dispense.getStatusEnum());
    }

    /**
     * 按主键加载发放单（不存在或已软删时 {@code null}）。
     *
     * @param id 发放单主键
     * @return 发放单或 {@code null}
     */
    public MedicationDispense findById(Long id) {
        if (id == null) {
            return null;
        }
        MedicationDispense dispense = medicationDispenseRepository.findById(id).orElse(null);
        if (dispense == null || Boolean.TRUE.equals(dispense.getIsDeleted())) {
            return null;
        }
        return dispense;
    }

    /**
     * 按药品医嘱主键集合加载未软删发放行。
     *
     * @param medReqIds 药品医嘱主键集合
     * @return 发放行列表
     */
    public List<MedicationDispense> listByMedReqIds(Collection<Long> medReqIds) {
        if (medReqIds == null || medReqIds.isEmpty()) {
            return List.of();
        }
        List<MedicationDispense> rows = medicationDispenseRepository.findByMedReqIdIn(medReqIds);
        List<MedicationDispense> result = new ArrayList<>();
        for (MedicationDispense row : rows) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 按业务单号批量读发放行（AS-CUS-TRC-002 · 明细 Tab 患者 enrich）。
     *
     * @param busNos 业务单号集合
     * @return 未软删发放行列表
     */
    public List<MedicationDispense> listByBusNos(Collection<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String busNo : busNos) {
            String trimmed = trimToNull(busNo);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return medicationDispenseRepository.findByBusNoInAndIsDeletedFalse(normalized);
    }

    /**
     * 判断药品是否被有效发放引用。
     *
     * @param medicationId 药品定义主键
     * @return 存在引用时为 true
     */
    public boolean existsByMedicationId(Long medicationId) {
        if (medicationId == null) {
            return false;
        }
        return medicationDispenseRepository.existsByMedicationId(medicationId);
    }

    /**
     * 判断药品是否存在非终态发放行（调价提交互斥 BR-PRC-SUB-003：在途发药）。
     *
     * @param medicationId 药品定义主键
     * @return 存在在途发放时为 true
     */
    public boolean existsInTransitDispenseByMedicationId(Long medicationId) {
        if (medicationId == null) {
            return false;
        }
        // 非终态发放（待发、配药中、部分完成等）视为在途发药
        Specification<MedicationDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.MEDICATION_ID), medicationId));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(this.inTransitDispenseStatusCodes()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return medicationDispenseRepository.count(specification) > 0;
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
     * 列出已确认退药（REFUNDED）的退药类型发放行关联的药品医嘱主键。
     *
     * @return 退药医嘱主键列表
     */
    public List<Long> listRefundedRefundDispenseMedReqIds() {
        Specification<MedicationDispense> specification = (root, cq, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.DISPENSE_ENUM), MedicationDispenseType.REFUND),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.REFUNDED));
        List<MedicationDispense> rows = medicationDispenseRepository.findAll(specification);
        List<Long> medReqIds = new ArrayList<>();
        for (MedicationDispense row : rows) {
            if (row != null && row.getMedReqId() != null) {
                medReqIds.add(row.getMedReqId());
            }
        }
        return medReqIds;
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
     * 按执行记录主键集合批量查询药品发放行（取消执行 Dispense 分轨定位，DM-MED-002 §6.2.14）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 发放单实体列表；空入参返回空列表
     */
    public List<MedicationDispense> listMedicationDispensesByProcedureIds(Collection<Long> procedureIds) {
        if (procedureIds == null || procedureIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long procedureId : procedureIds) {
            if (procedureId != null) {
                distinctIds.add(procedureId);
            }
        }
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return medicationDispenseRepository.findByProcedureIdIn(distinctIds);
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
     * L0 发药单 slip 分页别名（DISSOLUTION §0.3 P1；与 {@link #pageMedicationDispenseSlip} 等价）。
     */
    public Page<MedicationDispenseSlipQueryModel> pageSlip(MedicationDispenseSlipPageQuery query) {
        return this.pageMedicationDispenseSlip(query);
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
     * 发退药记录分页（AS-OP-PHA-004 / AS-IP-PHA-004）：仅已发/已退事实，按 {@code dispense_time} 倒序。
     *
     * @param query 记录分页入参；跨聚合筛选项须由应用层预解析写入 resolved 字段
     * @return 发放事实读模型页；无匹配时为空页
     */
    public Page<MedicationDispenseSlipQueryModel> pageMedicationDispenseRecords(MedicationDispenseRecordPageQuery query) {
        if (query == null) {
            query = new MedicationDispenseRecordPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.DISPENSE_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        PageRequest pageable = PageRequest.of(pageNo, pageSize, sort);
        Specification<MedicationDispense> specification = this.buildMedicationDispenseRecordSpecification(query);
        Page<MedicationDispense> page = medicationDispenseRepository.findAll(specification, pageable);
        List<MedicationDispenseSlipQueryModel> models = new ArrayList<>();
        for (MedicationDispense row : page.getContent()) {
            models.add(this.toSlipQueryModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 发退药记录全量列表（导出）：筛选口径与 {@link #pageMedicationDispenseRecords} 一致；行数受 {@link MedicationDispenseRecordPageQuery#getBoundedPageSize()} 约束。
     *
     * @param query 记录查询入参；导出场景须设置 {@code pageSize} 为导出行上限
     * @return 发放事实读模型列表；无匹配时返回空列表
     */
    public List<MedicationDispenseSlipQueryModel> listMedicationDispenseRecords(MedicationDispenseRecordPageQuery query) {
        if (query == null) {
            query = new MedicationDispenseRecordPageQuery();
        }
        int maxRows = query.getBoundedPageSize();
        Specification<MedicationDispense> specification = this.buildMedicationDispenseRecordSpecification(query);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.DISPENSE_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        // 导出全量列表：下推 LIMIT，避免 findAll 无界拉取
        PageRequest pageable = PageRequest.of(0, maxRows, sort);
        Page<MedicationDispense> page = medicationDispenseRepository.findAll(specification, pageable);
        List<MedicationDispenseSlipQueryModel> models = new ArrayList<>();
        for (MedicationDispense row : page.getContent()) {
            models.add(this.toSlipQueryModel(row));
        }
        return models;
    }

    /**
     * 组装发退药记录列表的 JPA 动态谓词（{@code dispense_time} 轴；不含发药工作台 supersede 排除）。
     *
     * @param query 记录分页入参
     * @return 供 {@link MedicationDispenseRepository#findAll} 使用的规约
     */
    private Specification<MedicationDispense> buildMedicationDispenseRecordSpecification(
        MedicationDispenseRecordPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            MedicationDispenseRecordPageQuery effectiveQuery = query;
            if (effectiveQuery == null) {
                effectiveQuery = new MedicationDispenseRecordPageQuery();
            }
            predicates.add(this.buildMedicationDispenseRecordTypePredicate(root, cb, effectiveQuery));
            if (effectiveQuery.getLocationId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), effectiveQuery.getLocationId()));
            }
            if (effectiveQuery.getPractitionerId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PRACTITIONER_ID), effectiveQuery.getPractitionerId()));
            }
            this.applyResolvedEncounterIdsForScene(predicates, root, cb, effectiveQuery.getResolvedEncounterIdsForScene());
            if (effectiveQuery.getResolvedEncounterIdsForScene() == null) {
                this.applyEncounterClassScenePredicate(predicates, root, cb, criteriaQuery, effectiveQuery);
            }
            this.applyResolvedMedicationIds(predicates, root, cb, effectiveQuery.getResolvedMedicationIdsForCategory());
            this.applyResolvedMedicationIds(predicates, root, cb, effectiveQuery.getResolvedMedicationIdsForDrugKeyword());
            this.applyResolvedEncounterIds(predicates, root, cb, effectiveQuery.getResolvedEncounterIdsForPatientKeyword());
            this.applyDispenseTimeWindow(predicates, root, cb, effectiveQuery);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 记录类型 + 场景正向发放类型谓词（发/退 OR 组合）。
     */
    private Predicate buildMedicationDispenseRecordTypePredicate(
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        MedicationDispenseRecordPageQuery query) {
        String recordType = StringUtils.trimToNull(query.getRecordTypeCode());
        if (recordType == null) {
            recordType = PharmacyDispenseRecordConstants.RECORD_TYPE_ALL;
        }
        Predicate forwardPredicate = this.buildMedicationDispenseRecordForwardPredicate(root, cb, query);
        Predicate refundPredicate = cb.and(
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.REFUNDED),
            cb.equal(root.get(FieldNameConstants.DISPENSE_ENUM), MedicationDispenseType.REFUND));
        if (PharmacyDispenseRecordConstants.RECORD_TYPE_DISPENSE.equals(recordType)) {
            return forwardPredicate;
        }
        if (PharmacyDispenseRecordConstants.RECORD_TYPE_REFUND.equals(recordType)) {
            return refundPredicate;
        }
        return cb.or(forwardPredicate, refundPredicate);
    }

    /**
     * 正向已发药行：{@code COMPLETED} 且 {@code dispense_enum} 落在门诊/住院场景集合。
     */
    private Predicate buildMedicationDispenseRecordForwardPredicate(
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        MedicationDispenseRecordPageQuery query) {
        List<MedicationDispenseType> forwardTypes = new ArrayList<>();
        if (Boolean.TRUE.equals(query.getOutpatientMedicationOnly())) {
            forwardTypes.add(MedicationDispenseType.OUTPATIENT);
            forwardTypes.add(MedicationDispenseType.TEMPORARY);
        } else if (Boolean.TRUE.equals(query.getInpatientMedicationOnly())) {
            forwardTypes.add(MedicationDispenseType.INPATIENT);
            forwardTypes.add(MedicationDispenseType.TEMPORARY);
        }
        if (forwardTypes.isEmpty()) {
            // 未指定场景时正向行不可命中，避免与退药行混查时误纳入全表 COMPLETED
            return cb.equal(cb.literal(1), cb.literal(0));
        }
        return cb.and(
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.COMPLETED),
            root.get(FieldNameConstants.DISPENSE_ENUM).in(forwardTypes));
    }

    /**
     * 就诊场景预解析主键集合 → {@code encounter_id IN}；空列表表示无命中须拒查。
     */
    private void applyResolvedEncounterIdsForScene(
        List<Predicate> predicates,
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        List<Long> resolvedEncounterIdsForScene) {
        if (resolvedEncounterIdsForScene == null) {
            return;
        }
        if (resolvedEncounterIdsForScene.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(resolvedEncounterIdsForScene));
    }

    /**
     * 门诊/住院场景标志下，按就诊类别（AMB/IMP）收窄退药行及 TEMPORARY 正向行；与 {@code resolvedEncounterIdsForScene} 互斥。
     */
    private void applyEncounterClassScenePredicate(
        List<Predicate> predicates,
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        CriteriaQuery<?> criteriaQuery,
        MedicationDispenseRecordPageQuery query) {
        String encounterClassCode = null;
        if (Boolean.TRUE.equals(query.getInpatientMedicationOnly())) {
            encounterClassCode = EncounterClass.IMP.getCode();
        } else if (Boolean.TRUE.equals(query.getOutpatientMedicationOnly())) {
            encounterClassCode = EncounterClass.AMB.getCode();
        }
        if (encounterClassCode == null) {
            return;
        }
        Subquery<Long> encounterSceneSubquery = criteriaQuery.subquery(Long.class);
        Root<Encounter> encounterRoot = encounterSceneSubquery.from(Encounter.class);
        encounterSceneSubquery.select(encounterRoot.get(FieldNameConstants.ID));
        encounterSceneSubquery.where(
            cb.equal(encounterRoot.get(FieldNameConstants.ID), root.get(FieldNameConstants.ENCOUNTER_ID)),
            cb.isFalse(encounterRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(encounterRoot.get(FieldNameConstants.CLASS_ENUM), encounterClassCode));
        predicates.add(cb.exists(encounterSceneSubquery));
    }

    /**
     * 患者关键字预解析就诊主键 → {@code encounter_id IN}；空列表表示无命中须拒查。
     */
    private void applyResolvedEncounterIds(
        List<Predicate> predicates,
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        List<Long> resolvedEncounterIds) {
        if (resolvedEncounterIds == null) {
            return;
        }
        if (resolvedEncounterIds.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(resolvedEncounterIds));
    }

    /**
     * 药品/项目类型预解析主键 → {@code medication_id IN}；空列表表示无命中须拒查。
     */
    private void applyResolvedMedicationIds(
        List<Predicate> predicates,
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        List<Long> ids) {
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.MEDICATION_ID).in(ids));
    }

    /**
     * {@code dispense_time} 闭区间；有时间窗时排除 {@code dispense_time} 为空的行。
     */
    private void applyDispenseTimeWindow(
        List<Predicate> predicates,
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        MedicationDispenseRecordPageQuery query) {
        if (query.getStartDate() != null || query.getEndDate() != null) {
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.DISPENSE_TIME)));
        }
        if (query.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.DISPENSE_TIME), query.getStartDate()));
        }
        if (query.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.DISPENSE_TIME), query.getEndDate()));
        }
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
                } else if (query.getLocationIds() != null && !query.getLocationIds().isEmpty()) {
                    predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(query.getLocationIds()));
                }
                if (query.getEncounterId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
                }
                if (query.getExcludeEncounterId() != null) {
                    predicates.add(cb.notEqual(root.get(FieldNameConstants.ENCOUNTER_ID), query.getExcludeEncounterId()));
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
                        if (StringUtils.isEmpty(code)) {
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
                } else if (StringUtils.isNotEmpty(query.getStatusEnum())) {
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
                    // 按患者发药（AS-IP-PHA-001）排除已绑定汇总单的发放行
                    this.applyExcludeSummaryBoundDispenseRows(predicates, root, cb);
                }
                if (query.getRequestStatusEnumIn() != null && !query.getRequestStatusEnumIn().isEmpty()) {
                    if (query.getResolvedMedReqIdsForRequestStatus() == null
                        || query.getResolvedMedReqIdsForRequestStatus().isEmpty()) {
                        predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
                    } else {
                        predicates.add(root.get(FieldNameConstants.MED_REQ_ID).in(query.getResolvedMedReqIdsForRequestStatus()));
                    }
                }
                if (query.getOrderingDepartmentId() != null) {
                    if (query.getResolvedMedReqIdsForOrderingDepartment() == null
                        || query.getResolvedMedReqIdsForOrderingDepartment().isEmpty()) {
                        predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
                    } else {
                        predicates.add(root.get(FieldNameConstants.MED_REQ_ID)
                            .in(query.getResolvedMedReqIdsForOrderingDepartment()));
                    }
                }
                if (query.getResolvedEncounterIdsForKeyword() != null) {
                    if (query.getResolvedEncounterIdsForKeyword().isEmpty()) {
                        predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
                    } else {
                        predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(query.getResolvedEncounterIdsForKeyword()));
                    }
                }
                if (StringUtils.isNotEmpty(query.getLotNumber())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NUMBER), query.getLotNumber()));
                }
                if (StringUtils.isNotEmpty(query.getSummaryNo())) {
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
                    this.applySupersededForwardMedReqExclusion(predicates, root, cb, query.getResolvedSupersededForwardMedReqIds());
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
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            if (DispenseStatus.COMPLETED.getCode().equals(query.getStatusEnum().trim())) {
                return true;
            }
        }
        if (query.getDispenseStatusEnumIn() == null || query.getDispenseStatusEnumIn().isEmpty()) {
            return false;
        }
        for (String code : query.getDispenseStatusEnumIn()) {
            if (StringUtils.isEmpty(code)) {
                continue;
            }
            if (DispenseStatus.COMPLETED.getCode().equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 排除已被确认退药收口的正向 COMPLETED 发放行（预解析正向 medReqId 集合，禁止 EM 跨根 EXISTS）。
     */
    private void applySupersededForwardMedReqExclusion(
        List<Predicate> predicates,
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        List<Long> supersededForwardMedReqIds
    ) {
        if (supersededForwardMedReqIds == null || supersededForwardMedReqIds.isEmpty()) {
            return;
        }
        predicates.add(cb.or(
            cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.COMPLETED),
            cb.equal(root.get(FieldNameConstants.DISPENSE_ENUM), MedicationDispenseType.REFUND),
            cb.not(root.get(FieldNameConstants.MED_REQ_ID).in(supersededForwardMedReqIds))));
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
     * 住院药房退药患者队列：分页返回「存在待退药或已退药药品发放事实且在院」的就诊主键（AS-IP-PHA-003 IF-IPR-02）。
     *
     * @param query 查询入参；{@code null} 视为空条件对象
     * @return 就诊主键页（按本就诊下命中发放行的最近 {@code status_changed_time} 倒序）
     */
    public Page<Long> pageEncounterIdsForInpatientPharmacyReturn(InpatientPharmacyReturnEncounterPageQuery query) {
        if (query == null) {
            query = new InpatientPharmacyReturnEncounterPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = this.resolveQueuePageSize(query);
        if (pageSize > 100) {
            pageSize = 100;
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<MedicationDispense> countRoot = countCq.from(MedicationDispense.class);
        Predicate queuePredicate = this.buildInpatientPharmacyReturnEncounterQueuePredicate(countRoot, countCq, cb, query);
        countCq.select(cb.countDistinct(countRoot.get(FieldNameConstants.ENCOUNTER_ID)));
        countCq.where(queuePredicate);
        Long totalRow = this.entityManager.createQuery(countCq).getSingleResult();
        long total = 0L;
        if (totalRow != null) {
            total = totalRow.longValue();
        }
        CriteriaQuery<Long> dataCq = cb.createQuery(Long.class);
        Root<MedicationDispense> md = dataCq.from(MedicationDispense.class);
        Predicate dataPredicate = this.buildInpatientPharmacyReturnEncounterQueuePredicate(md, dataCq, cb, query);
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

    /**
     * 门诊退药患者队列 EXISTS 谓词：药房/退药类型与待退/已退状态。
     *
     * @param md    发放根
     * @param cq    外层查询
     * @param cb    CriteriaBuilder
     * @param query 退药患者队列入参
     * @return 子查询谓词
     */
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
        if (query.getDepartmentId() != null || StringUtils.isNotEmpty(query.getKeyword())) {
            this.applyEncounterScopePredicate(predicates, md, cb, query.getResolvedEncounterScope());
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * 住院退药就诊队列 EXISTS 谓词：住院/临时/退药类型与待退/已退状态。
     *
     * @param md    发放根
     * @param cq    外层查询
     * @param cb    CriteriaBuilder
     * @param query 住院退药就诊队列入参
     * @return 子查询谓词
     */
    private Predicate buildInpatientPharmacyReturnEncounterQueuePredicate(
        Root<MedicationDispense> md,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        InpatientPharmacyReturnEncounterPageQuery query
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(md.get(FieldNameConstants.IS_DELETED)));
        List<Long> locationIds = query.getLocationIds();
        if (locationIds != null && !locationIds.isEmpty()) {
            predicates.add(md.get(FieldNameConstants.LOCATION_ID).in(locationIds));
        }
        List<DispenseStatus> returnStatuses = List.of(DispenseStatus.PENDING_REFUND, DispenseStatus.REFUNDED);
        predicates.add(md.get(FieldNameConstants.STATUS_ENUM).in(returnStatuses));
        List<MedicationDispenseType> inpatientTypes = List.of(
            MedicationDispenseType.INPATIENT, MedicationDispenseType.TEMPORARY, MedicationDispenseType.REFUND);
        predicates.add(md.get(FieldNameConstants.DISPENSE_ENUM).in(inpatientTypes));
        this.applyEncounterScopePredicate(predicates, md, cb, query.getResolvedEncounterScope());
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * 门诊待发药就诊队列 EXISTS 谓词：门诊/临时类型、状态多选与退药收口过滤。
     *
     * @param md    发放根
     * @param cq    外层查询
     * @param cb    CriteriaBuilder
     * @param query 门诊待发药就诊队列入参
     * @return 子查询谓词
     */
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
            this.applySupersededForwardMedReqExclusion(predicates, md, cb, query.getResolvedSupersededForwardMedReqIds());
        }
        List<MedicationDispenseType> outpatientTypes = List.of(MedicationDispenseType.OUTPATIENT, MedicationDispenseType.TEMPORARY);
        predicates.add(md.get(FieldNameConstants.DISPENSE_ENUM).in(outpatientTypes));
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            this.applyEncounterScopePredicate(predicates, md, cb, query.getResolvedEncounterScope());
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
            if (StringUtils.isEmpty(code)) {
                continue;
            }
            if (DispenseStatus.COMPLETED.getCode().equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 住院待发药就诊队列 EXISTS 谓词：住院/临时类型，排除汇总绑定行。
     *
     * @param md    发放根
     * @param cq    外层查询
     * @param cb    CriteriaBuilder
     * @param query 住院待发药就诊队列入参
     * @return 子查询谓词
     */
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
        // 按患者发药（AS-IP-PHA-001）不承接已绑定汇总单的发放行；汇总路径见 AS-IP-PHA-002
        this.applyExcludeSummaryBoundDispenseRows(predicates, md, cb);
        this.applyEncounterScopePredicate(predicates, md, cb, query.getResolvedEncounterScope());
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * 排除已绑定 {@code summary_no} 的发放行（汇总领药/汇总核发路径，不属于按患者发药工作站）。
     */
    private void applyExcludeSummaryBoundDispenseRows(
        List<Predicate> predicates,
        Root<MedicationDispense> root,
        CriteriaBuilder cb) {
        predicates.add(cb.or(
            cb.isNull(root.get(FieldNameConstants.SUMMARY_NO)),
            cb.equal(root.get(FieldNameConstants.SUMMARY_NO), "")));
    }

    /**
     * 将预解析就诊 id 集合追加为 encounter_id IN 谓词。
     *
     * @param predicates             条件列表（追加）
     * @param md                     发放根
     * @param cb                     CriteriaBuilder
     * @param resolvedEncounterScope 预解析就诊主键；空则不追加
     */
    private void applyEncounterScopePredicate(
        List<Predicate> predicates,
        Root<MedicationDispense> md,
        CriteriaBuilder cb,
        List<Long> resolvedEncounterScope) {
        if (resolvedEncounterScope == null) {
            return;
        }
        if (resolvedEncounterScope.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(md.get(FieldNameConstants.ENCOUNTER_ID).in(resolvedEncounterScope));
    }

    /**
     * 患者队列分页大小：入参有界页大小，否则默认页大小。
     *
     * @param query 分页入参
     * @return 页大小
     */
    private int resolveQueuePageSize(AbstractPageQuery query) {
        if (query.getPageSize() != null && query.getPageSize() > 0) {
            return query.getBoundedPageSize();
        }
        return GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
    }

    /**
     * 发放状态 code 列表 → DispenseStatus 枚举列表（非法 code 跳过）。
     *
     * @param codes 状态 code 列表
     * @return 枚举列表；空入参返回空列表
     */
    private List<DispenseStatus> resolveDispenseStatusEnumInForPredicate(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<DispenseStatus> ordered = new LinkedHashSet<>();
        for (String raw : codes) {
            if (StringUtils.isEmpty(raw)) {
                continue;
            }
            DispenseStatus parsed = EnumUtil.fromCode(DispenseStatus.class, raw.trim());
            if (parsed != null) {
                ordered.add(parsed);
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 住院护士站汇总领药 · 待汇总发放明细分页（**AS-IP-NUR-010 · IF-IP-SUM-01**；**BR-IP-SUM-002**）。
     *
     * <p>基线：{@code PREPARATION} + {@code procedure_id} 非空 + 未绑定 {@code summary_no}；账单单价由应用层 {@code InpatientMedicationDispenseChargePricingLoader} 协作回填。</p>
     *
     * @param query 分页与过滤入参；{@code encounterIds} 为空时返回空页
     * @return 待汇总明细分页
     */
    public Page<MedicationDispensePickupPendingQueryModel> pageMedicationDispensePickupPending(
        MedicationDispensePickupPendingPageQuery query) {
        if (query == null || query.getEncounterIds() == null || query.getEncounterIds().isEmpty()) {
            return Page.empty();
        }
        int springIndex = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        PageRequest pageable = PageRequest.of(
            springIndex,
            pageSize,
            Sort.by(
                Sort.Order.asc("plannedDispenseTime"),
                Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        Specification<MedicationDispense> specification = this.buildPickupPendingSpecification(query);
        Page<MedicationDispense> page = medicationDispenseRepository.findAll(specification, pageable);
        List<MedicationDispensePickupPendingQueryModel> models = new ArrayList<>();
        for (MedicationDispense row : page.getContent()) {
            models.add(this.toPickupPendingQueryModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 组装待汇总领药明细的动态谓词（**BR-IP-SUM-002**）。
     */
    private Specification<MedicationDispense> buildPickupPendingSpecification(MedicationDispensePickupPendingPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.PREPARATION));
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.PROCEDURE_ID)));
            predicates.add(cb.or(
                cb.isNull(root.get(FieldNameConstants.SUMMARY_NO)),
                cb.equal(root.get(FieldNameConstants.SUMMARY_NO), "")));
            predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(query.getEncounterIds()));
            predicates.add(root.get(FieldNameConstants.DISPENSE_ENUM).in(List.of(
                MedicationDispenseType.INPATIENT,
                MedicationDispenseType.TEMPORARY)));
            if (query.getResolvedMedReqIds() != null) {
                if (query.getResolvedMedReqIds().isEmpty()) {
                    predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
                } else {
                    predicates.add(root.get(FieldNameConstants.MED_REQ_ID).in(query.getResolvedMedReqIds()));
                }
            }
            if (query.getQueryDeadline() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("plannedDispenseTime"), query.getQueryDeadline()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 待汇总领药发放行 → 读模型（医嘱与数量事实）。
     *
     * @param dispense 发放实体
     * @return 待领药查询模型
     */
    private MedicationDispensePickupPendingQueryModel toPickupPendingQueryModel(MedicationDispense dispense) {
        MedicationDispensePickupPendingQueryModel model = new MedicationDispensePickupPendingQueryModel();
        model.setDispenseId(dispense.getId());
        model.setProcedureId(dispense.getProcedureId());
        model.setPlannedDispenseTime(dispense.getPlannedDispenseTime());
        model.setEncounterId(dispense.getEncounterId());
        model.setPatientId(dispense.getPatientId());
        model.setMedicationId(dispense.getMedicationId());
        model.setMedReqId(dispense.getMedReqId());
        model.setLocationId(dispense.getLocationId());
        model.setQuantity(dispense.getQuantity());
        model.setUnitCode(dispense.getUnitCode());
        model.setDosageInstruction(dispense.getDosageInstruction());
        model.setMethodCode(dispense.getMethodCode());
        model.setFrequencyCode(dispense.getFrequencyCode());
        return model;
    }

    /**
     * 住院护士站追溯码分配 · 待分配患者队列分页（**AS-IP-NUR-015 · IF-IP-TRC-01**；**BR-IP-TRC-002**）。
     *
     * <p>命中条件：汇总路径 {@code COMPLETED} 且 {@code trace_no} 为空的发放行；按就诊去重分页。</p>
     *
     * @param query 分页与可见就诊集合；{@code encounterIds} 为空时返回空页
     * @return 待办就诊行（含待分配汇总组计数）
     */
    public Page<EncounterPendingTraceAssignmentQueryModel> pageEncountersPendingTraceAssignment(
        EncountersPendingTraceAssignmentPageQuery query) {
        if (query == null || query.getEncounterIds() == null || query.getEncounterIds().isEmpty()) {
            return Page.empty();
        }
        int pageNo = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<MedicationDispense> countRoot = countCq.from(MedicationDispense.class);
        Predicate pendingEncounterPredicate =
            this.buildPendingTraceAssignmentEncounterPredicate(countRoot, cb, query.getEncounterIds());
        countCq.select(cb.countDistinct(countRoot.get(FieldNameConstants.ENCOUNTER_ID)));
        countCq.where(pendingEncounterPredicate);
        Long totalRow = this.entityManager.createQuery(countCq).getSingleResult();
        long total = 0L;
        if (totalRow != null) {
            total = totalRow.longValue();
        }
        CriteriaQuery<Long> dataCq = cb.createQuery(Long.class);
        Root<MedicationDispense> md = dataCq.from(MedicationDispense.class);
        Predicate dataPredicate = this.buildPendingTraceAssignmentEncounterPredicate(md, cb, query.getEncounterIds());
        dataCq.select(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.where(dataPredicate);
        dataCq.groupBy(md.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.orderBy(cb.desc(cb.max(md.get(FieldNameConstants.CREATED_AT))));
        TypedQuery<Long> typed = this.entityManager.createQuery(dataCq);
        typed.setFirstResult(pageNo * pageSize);
        typed.setMaxResults(pageSize);
        List<Long> encounterIds = typed.getResultList();
        PageRequest pageable = PageRequest.of(pageNo, pageSize);
        if (encounterIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        List<MedicationDispense> scopeRows =
            medicationDispenseRepository.findByEncounterIdIn(encounterIds);
        Map<Long, Integer> pendingGroupCountByEncounter =
            this.countPendingTraceAssignmentGroupsByEncounter(scopeRows);
        List<EncounterPendingTraceAssignmentQueryModel> models = new ArrayList<>();
        for (Long encounterId : encounterIds) {
            EncounterPendingTraceAssignmentQueryModel model = new EncounterPendingTraceAssignmentQueryModel();
            model.setEncounterId(encounterId);
            Integer pendingCount = pendingGroupCountByEncounter.get(encounterId);
            if (pendingCount == null) {
                pendingCount = 0;
            }
            model.setPendingGroupCount(pendingCount);
            Long patientId = this.resolvePatientIdForEncounter(scopeRows, encounterId);
            model.setPatientId(patientId);
            models.add(model);
        }
        return new PageImpl<>(models, pageable, total);
    }

    /**
     * 住院护士站追溯码分配 · 按就诊加载药+批汇总行（**AS-IP-NUR-015 · IF-IP-TRC-02**）。
     *
     * @param encounterId 就诊主键
     * @return 汇总组列表；无汇总路径已核发发放行时返回空表
     */
    public List<PatientTraceAssignmentAggregateLineQueryModel> listPatientTraceAssignmentAggregateLines(
        Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<MedicationDispense> rows = medicationDispenseRepository.findByEncounterId(encounterId);
        return this.buildPatientTraceAssignmentAggregateLines(rows);
    }

    /**
     * 组装「待分配患者」队列谓词：存在汇总路径且 trace 仍空的发放行。
     */
    private Predicate buildPendingTraceAssignmentEncounterPredicate(
        Root<MedicationDispense> root,
        CriteriaBuilder cb,
        List<Long> encounterIds) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), DispenseStatus.COMPLETED));
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.SUMMARY_NO)));
        predicates.add(cb.notEqual(root.get(FieldNameConstants.SUMMARY_NO), ""));
        predicates.add(cb.or(
            cb.isNull(root.get(FieldNameConstants.TRACE_NO)),
            cb.equal(root.get(FieldNameConstants.TRACE_NO), "")));
        predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIds));
        predicates.add(root.get(FieldNameConstants.DISPENSE_ENUM).in(List.of(
            MedicationDispenseType.INPATIENT,
            MedicationDispenseType.TEMPORARY)));
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * 统计各就诊下仍待分配 trace 的汇总组数量（药+批维度）。
     */
    private Map<Long, Integer> countPendingTraceAssignmentGroupsByEncounter(List<MedicationDispense> scopeRows) {
        Map<Long, Map<String, Boolean>> pendingByEncounter = new HashMap<>();
        for (MedicationDispense row : scopeRows) {
            if (!this.isSummaryPathTraceAssignableRow(row)) {
                continue;
            }
            Long encounterId = row.getEncounterId();
            if (encounterId == null) {
                continue;
            }
            String groupKey = this.buildTraceAssignmentGroupKey(row);
            Map<String, Boolean> groups = pendingByEncounter.computeIfAbsent(encounterId, key -> new HashMap<>());
            boolean groupPending = groups.getOrDefault(groupKey, Boolean.FALSE);
            if (StringUtils.isEmpty(row.getTraceNo())) {
                groupPending = true;
            }
            groups.put(groupKey, groupPending);
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Map.Entry<Long, Map<String, Boolean>> entry : pendingByEncounter.entrySet()) {
            int pendingCount = 0;
            for (Boolean pending : entry.getValue().values()) {
                if (Boolean.TRUE.equals(pending)) {
                    pendingCount = pendingCount + 1;
                }
            }
            if (pendingCount > 0) {
                result.put(entry.getKey(), pendingCount);
            }
        }
        return result;
    }

    /**
     * 从发放行集合解析就诊对应患者主键（取首条命中行）。
     */
    private Long resolvePatientIdForEncounter(List<MedicationDispense> scopeRows, Long encounterId) {
        for (MedicationDispense row : scopeRows) {
            if (encounterId.equals(row.getEncounterId()) && row.getPatientId() != null) {
                return row.getPatientId();
            }
        }
        return null;
    }

    /**
     * 将汇总路径已核发发放行按 summaryNo+药品+批号聚合为护士追溯分配展示组。
     */
    private List<PatientTraceAssignmentAggregateLineQueryModel> buildPatientTraceAssignmentAggregateLines(
        List<MedicationDispense> rows) {
        Map<String, PatientTraceAssignmentAggregateLineQueryModel> grouped = new LinkedHashMap<>();
        for (MedicationDispense row : rows) {
            if (!this.isSummaryPathTraceAssignableRow(row)) {
                continue;
            }
            String groupKey = this.buildTraceAssignmentGroupKey(row);
            PatientTraceAssignmentAggregateLineQueryModel aggregate = grouped.get(groupKey);
            if (aggregate == null) {
                aggregate = new PatientTraceAssignmentAggregateLineQueryModel();
                aggregate.setGroupKey(groupKey);
                aggregate.setSummaryNo(row.getSummaryNo());
                aggregate.setMedicationId(row.getMedicationId());
                aggregate.setLotNumber(row.getLotNumber());
                aggregate.setEncounterId(row.getEncounterId());
                aggregate.setPatientId(row.getPatientId());
                aggregate.setDispenseIds(new ArrayList<>());
                aggregate.setDispenseLines(new ArrayList<>());
                aggregate.setAssignmentPending(Boolean.FALSE);
                grouped.put(groupKey, aggregate);
            }
            aggregate.getDispenseIds().add(row.getId());
            PatientTraceAssignmentDispenseLineQueryModel line = new PatientTraceAssignmentDispenseLineQueryModel();
            line.setDispenseId(row.getId());
            line.setQuantity(row.getQuantity());
            line.setUnitCode(row.getUnitCode());
            line.setTraceNo(row.getTraceNo());
            aggregate.getDispenseLines().add(line);
            if (StringUtils.isEmpty(aggregate.getAssignedTraceNo()) && StringUtils.isNotEmpty(row.getTraceNo())) {
                aggregate.setAssignedTraceNo(row.getTraceNo());
            }
            if (StringUtils.isEmpty(row.getTraceNo())) {
                aggregate.setAssignmentPending(Boolean.TRUE);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 汇总路径且已核发的发放行，才进入护士追溯分配读/写范围。
     */
    private boolean isSummaryPathTraceAssignableRow(MedicationDispense row) {
        if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
            return false;
        }
        if (!DispenseStatus.COMPLETED.equals(row.getStatusEnum())) {
            return false;
        }
        if (StringUtils.isEmpty(row.getSummaryNo())) {
            return false;
        }
        if (!MedicationDispenseType.INPATIENT.equals(row.getDispenseEnum())
            && !MedicationDispenseType.TEMPORARY.equals(row.getDispenseEnum())) {
            return false;
        }
        return true;
    }

    /**
     * 护士追溯分配汇总组稳定键（**BR-IP-TRC-004**）。
     */
    private String buildTraceAssignmentGroupKey(MedicationDispense row) {
        String summaryNo = row.getSummaryNo();
        Long medicationId = row.getMedicationId();
        String lotNumber = row.getLotNumber();
        if (lotNumber == null) {
            lotNumber = "";
        }
        return summaryNo + ":" + medicationId + ":" + lotNumber;
    }

}
