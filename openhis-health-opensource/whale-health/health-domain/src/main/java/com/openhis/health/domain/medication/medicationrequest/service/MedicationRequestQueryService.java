/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.service;

import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestHistoryQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.OutpatientSignedPrescriptionSummaryCountQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.query.OutpatientSignedPrescriptionDrugLinePageQuery;
import com.openhis.health.domain.medication.medicationrequest.repository.MedicationRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsPredicates;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药品医嘱/申请读侧查询服务。
 *
 * <p>职责：按就诊与患者维度查询申请列表与历史，并提供删除、签退等动作前的状态门控；只读不写库。</p>
 * <p>聚合边界：仅本聚合 {@link MedicationRequestRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicationRequestQueryService {

    private final MedicationRequestRepository medicationRequestRepository;
    /** Criteria 去重计数（与 EncounterQueryService#countDistinctBusNo 同构）。 */
    private final EntityManager entityManager;


    /**
     * 按主键加载药品申请详情。
     * <p>内部复用 mustGet 进行存在性校验，再调用 toDetailModel 装配详情。</p>
     *
     * @param id 药品申请标识
     * @return 药品申请详情模型
     */
    public MedicationRequestDetailQueryModel loadMedicationRequestById(Long id) {
        return this.toDetailModel(this.mustGet(id));
    }

    /**
     * 解析药品申请关联的药品定义主键（展示/批量预热，不存在时返回 null）。
     *
     * @param requestId 药品申请主键
     * @return 药品定义主键或 null
     */
    /**
     * 按主键加载药品申请（不存在或已软删时 {@code null}）。
     *
     * @param id 药品申请主键
     * @return 药品申请或 {@code null}
     */
    public MedicationRequest findById(Long id) {
        if (id == null) {
            return null;
        }
        MedicationRequest request = medicationRequestRepository.findById(id).orElse(null);
        if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
            return null;
        }
        return request;
    }

    /**
     * 按主键集合批量加载未软删药品申请。
     *
     * @param ids 主键集合
     * @return 药品申请列表
     */
    public List<MedicationRequest> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<MedicationRequest> rows = medicationRequestRepository.findAllById(ids);
        List<MedicationRequest> result = new ArrayList<>();
        for (MedicationRequest row : rows) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 判断药品是否被有效药品申请引用。
     *
     * @param medicationId 药品定义主键
     * @return 存在引用时为 true
     */
    public boolean existsByMedicationId(Long medicationId) {
        if (medicationId == null) {
            return false;
        }
        return medicationRequestRepository.existsByMedicationId(medicationId);
    }

    /**
     * 判断药品是否存在非终态医嘱申请（调价提交互斥 BR-PRC-SUB-003：在途医嘱）。
     *
     * @param medicationId 药品定义主键 {@code med_medication_definition.id}
     * @return 存在在途申请时为 true
     */
    public boolean existsInTransitRequestByMedicationId(Long medicationId) {
        if (medicationId == null) {
            return false;
        }
        // 非终态申请（待发、已签发、已校对、暂挂）视为在途医嘱
        Specification<MedicationRequest> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.MEDICATION_ID), medicationId));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(this.inTransitRequestStatusCodes()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return medicationRequestRepository.count(specification) > 0;
    }

    /**
     * 在途医嘱申请状态码：待发、已签发、已校对、暂挂（非 COMPLETED/CANCELLED/STOPPED）。
     *
     * @return 状态 code 列表
     */
    private List<String> inTransitRequestStatusCodes() {
        List<String> codes = new ArrayList<>();
        codes.add(RequestStatus.DRAFT.getCode());
        codes.add(RequestStatus.ACTIVE.getCode());
        codes.add(RequestStatus.VERIFIED.getCode());
        codes.add(RequestStatus.HOLD.getCode());
        return codes;
    }

    public Long resolveMedicationDefinitionId(Long requestId) {
        if (requestId == null) {
            return null;
        }
        MedicationRequest request = medicationRequestRepository.findById(requestId).orElse(null);
        if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
            return null;
        }
        return request.getMedicationId();
    }

    /**
     * 加载门诊医生站药品签发后 FIFO 发放编排所需的医嘱字段切片。
     *
     * @param medReqId 药品申请主键
     * @return 只读上下文
     * @throws BusinessException 申请不存在时
     */
    public MedicationRequestFifoSignContext loadFifoSignContextOrThrow(Long medReqId) {
        // 单条与批量装载共用字段切片，避免双口径
        return this.toFifoSignContext(this.mustGet(medReqId));
    }

    /**
     * 按主键批量装载 FIFO 签发只读切片（不含目录分类码；分类由应用层 Loader 补齐）。
     *
     * <p>任一主键未命中则抛 {@link ErrorCodeConstants#MEDICATION_REQUEST_NOT_FOUND}，
     * 与 {@link #loadFifoSignContextOrThrow} / {@code mustGet} 同口径（含未过滤软删实体）。</p>
     *
     * @param medReqIds 药品申请主键集合；空则返回空 Map
     * @return 申请主键 → FIFO 上下文
     * @throws BusinessException 任一主键不存在
     */
    public Map<Long, MedicationRequestFifoSignContext> mapFifoSignContextsByIdsOrThrow(Collection<Long> medReqIds) {
        if (medReqIds == null || medReqIds.isEmpty()) {
            return Map.of();
        }
        // Pre：去重保序，忽略 null，避免重复查库
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long medReqId : medReqIds) {
            if (medReqId != null) {
                uniqueIds.add(medReqId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MedicationRequest> byId = new HashMap<>();
        for (MedicationRequest row : this.medicationRequestRepository.findAllById(uniqueIds)) {
            if (row == null || row.getId() == null) {
                continue;
            }
            byId.put(row.getId(), row);
        }
        Map<Long, MedicationRequestFifoSignContext> result = new HashMap<>();
        for (Long medReqId : uniqueIds) {
            MedicationRequest request = byId.get(medReqId);
            // 与 mustGet 一致：缺行即拒签，避免半批空上下文
            if (request == null) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在");
            }
            result.put(medReqId, this.toFifoSignContext(request));
        }
        return result;
    }

    /**
     * 药品申请实体 → FIFO 签发只读切片（不含 catalogCategoryCode）。
     *
     * @param request 已加载的药品申请
     * @return FIFO 上下文
     */
    private MedicationRequestFifoSignContext toFifoSignContext(MedicationRequest request) {
        MedicationRequestFifoSignContext ctx = new MedicationRequestFifoSignContext();
        ctx.setMedReqId(request.getId());
        // 处方号供签发挂费写入 ChargeItem，工作量按方去重依赖此字段
        ctx.setPrescriptionNo(request.getPrescriptionNo());
        ctx.setMedicationId(request.getMedicationId());
        ctx.setEncounterId(request.getEncounterId());
        ctx.setPatientId(request.getPatientId());
        ctx.setQuantity(request.getQuantity());
        ctx.setUnitCode(request.getUnitCode());
        ctx.setMethodCode(request.getMethodCode());
        return ctx;
    }

    /**
     * 按主键批量加载药品申请实体（退药明细等读组装用，仅限本聚合内仓储访问）。
     *
     * @param medicationRequestIds 药品申请主键集合
     * @return 申请 id → 实体；已删除或未命中时不包含该键
     */
    public Map<Long, MedicationRequest> mapEntitiesByIds(Collection<Long> medicationRequestIds) {
        Map<Long, MedicationRequest> map = new HashMap<>();
        if (medicationRequestIds == null || medicationRequestIds.isEmpty()) {
            return map;
        }
        for (MedicationRequest row : this.medicationRequestRepository.findAllById(medicationRequestIds)) {
            if (row == null || row.getId() == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            map.put(row.getId(), row);
        }
        return map;
    }

    /**
     * 按主键批量解析 {@code content_json}（执行单等读侧富化，禁止应用层直访仓储）。
     *
     * @param medicationRequestIds 药品申请主键集合
     * @return 申请 id → contentJson；无内容或未命中时不包含该键
     */
    public Map<Long, String> mapContentJsonByIds(Collection<Long> medicationRequestIds) {
        if (medicationRequestIds == null || medicationRequestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MedicationRequest> rows = this.medicationRequestRepository.findAllById(medicationRequestIds);
        Map<Long, String> map = new HashMap<>();
        for (MedicationRequest row : rows) {
            if (row == null || row.getId() == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (StringUtils.isEmpty(row.getContentJson())) {
                continue;
            }
            map.put(row.getId(), row.getContentJson());
        }
        return map;
    }

    /**
     * 按就诊查询药品申请列表。
     *
     * @param encounterId 就诊标识
     * @return 药品申请集合
     */
    public List<MedicationRequestListQueryModel> listMedicationRequestsByEncounter(Long encounterId) {
        List<MedicationRequest> requests = medicationRequestRepository.findByEncounterId(encounterId);
        List<MedicationRequestListQueryModel> result = new ArrayList<>();
        // Loop：遍历就诊下全部药品申请，逐条转换为列表展示模型
        for (MedicationRequest request : requests) {
            result.add(this.toListModel(request));
        }
        return result;
    }

    /**
     * 门诊医生站：按就诊与生成来源过滤药品申请列表（仓储下推，对齐 AS-OP-DOR-003 BR-ORD-010）。
     *
     * @param encounterId 就诊标识
     * @param generateSourceEnum 生成来源码
     * @return 列表
     */
    public List<MedicationRequestListQueryModel> listMedicationRequestsByEncounterForDoctorStation(
        Long encounterId, String generateSourceEnum) {
        if (encounterId == null || generateSourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "按医生站来源过滤药品申请时 encounterId 与 generateSourceEnum 不能为空");
        }
        List<MedicationRequest> requests = medicationRequestRepository.findByEncounterIdAndGenerateSourceEnum(
            encounterId, generateSourceEnum);
        List<MedicationRequestListQueryModel> result = new ArrayList<>();
        // Loop：遍历医生站来源过滤后的药品申请，逐条转换为列表展示模型
        for (MedicationRequest request : requests) {
            result.add(this.toListModel(request));
        }
        return result;
    }

    /**
     * 按就诊查询中药饮片处方列表（按方聚合：优先 {@code content_json.prescriptionGroupKey}，
     * 无 key 时回落 {@code prescription_no} / 行 id）。
     *
     * @param encounterId          就诊标识
     * @param generateSourceEnum   生成来源码（医生站通常为 {@link GenerateSource#DOCTOR_PRESCRIPTION}）
     * @return 按方聚合的中医处方读模型；无数据时返回空列表
     */
    public List<TcmHerbalPrescriptionGroupQueryModel> listTcmHerbalPrescriptionsByEncounter(
        Long encounterId, String generateSourceEnum) {
        return this.listTcmHerbalPrescriptionsByEncounter(encounterId, generateSourceEnum, null);
    }

    /**
     * 按就诊查询中药饮片处方列表（按方聚合），可选按长临嘱收窄。
     *
     * @param encounterId          就诊标识
     * @param generateSourceEnum   生成来源码（医生站通常为 {@link GenerateSource#DOCTOR_PRESCRIPTION}）
     * @param therapyEnum          治疗类型码（{@link com.openhis.health.domain.share.enums.TherapyType#getCode()}）；空则不过滤
     * @return 按方聚合的中医处方读模型；无数据时返回空列表
     */
    public List<TcmHerbalPrescriptionGroupQueryModel> listTcmHerbalPrescriptionsByEncounter(
        Long encounterId, String generateSourceEnum, String therapyEnum) {
        if (encounterId == null || generateSourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "查询就诊中药处方时 encounterId 与 generateSourceEnum 不能为空");
        }
        List<MedicationRequest> requests = medicationRequestRepository.findByEncounterIdAndGenerateSourceEnum(
            encounterId, generateSourceEnum);
        List<MedicationRequestListQueryModel> tcmLines = new ArrayList<>();
        // Pre：医生开立来源下再筛 tcm_flag，排除软删行；可选按 therapy_enum 收窄住院长临 Tab。
        for (MedicationRequest request : requests) {
            if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            if (!Boolean.TRUE.equals(request.getTcmFlag())) {
                continue;
            }
            if (StringUtils.isNotEmpty(therapyEnum) && !therapyEnum.equals(request.getTherapyEnum())) {
                continue;
            }
            // 读侧排除退药逆向复制行，避免中药 Tab 一收一退双行展示（BR-ORD-030 / BR-TCM-RX）
            if (request.getRefundMedicineId() != null) {
                continue;
            }
            tcmLines.add(this.toListModel(request));
        }
        // Asm：按 content_json.prescriptionGroupKey（优先）或 prescription_no 聚合为方。
        return this.groupTcmHerbalPrescriptionLines(tcmLines);
    }

    /**
     * 按就诊与前端方标识查找同方已有处方号（签退回草稿后同方 CREATE 行复用，避免拆成新方）。
     *
     * <p>仅扫本聚合 {@link MedicationRequestRepository}；匹配 {@code content_json.prescriptionGroupKey}。</p>
     *
     * @param encounterId            就诊主键
     * @param generateSourceEnum     生成来源码（医生站通常为 {@link GenerateSource#DOCTOR_PRESCRIPTION}）
     * @param prescriptionGroupKey   前端方标识（与暂存请求头一致）
     * @return 非空处方号；无匹配或入参空时 null
     */
    public String findTcmPrescriptionNoByGroupKey(
        Long encounterId, String generateSourceEnum, String prescriptionGroupKey) {
        if (encounterId == null || StringUtils.isEmpty(generateSourceEnum)
            || StringUtils.isEmpty(prescriptionGroupKey)) {
            return null;
        }
        String targetKey = prescriptionGroupKey.trim();
        List<MedicationRequest> requests = medicationRequestRepository.findByEncounterIdAndGenerateSourceEnum(
            encounterId, generateSourceEnum);
        // Loop：同方任一行已有处方号即复用（签退回 DRAFT 后仍保留 CP 号）
        for (MedicationRequest request : requests) {
            if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            if (!Boolean.TRUE.equals(request.getTcmFlag())) {
                continue;
            }
            // 退药逆向复制行不参与同方号继承
            if (request.getRefundMedicineId() != null) {
                continue;
            }
            if (StringUtils.isEmpty(request.getPrescriptionNo())) {
                continue;
            }
            String lineKey = this.readTcmContentJsonTextField(request.getContentJson(), "prescriptionGroupKey");
            if (targetKey.equals(lineKey)) {
                return request.getPrescriptionNo().trim();
            }
        }
        return null;
    }

    /**
     * 查询患者历史中药处方（含当前就诊已签方，按处方号聚合）。
     *
     * <p>{@code currentEncounterId} 用于入参校验；是否「本次」由应用层对照标记，本方法不再排除当前就诊。</p>
     *
     * @param patientId          患者标识
     * @param currentEncounterId 当前就诊标识（非空校验；列表含该就诊已签中药方）
     * @return 历史方摘要列表；无数据时返回空列表
     */
    public List<TcmHerbalPrescriptionHistoryGroupQueryModel> listTcmHerbalPrescriptionHistoryByPatient(
        Long patientId, Long currentEncounterId) {
        if (patientId == null || currentEncounterId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "查询患者历史中药处方时 patientId 与 currentEncounterId 不能为空");
        }
        // Pre：患者维度全量申请行（含当前就诊），再过滤医生开立已签中药方
        List<MedicationRequest> requests = medicationRequestRepository.findByPatientId(patientId);
        Map<String, TcmHerbalPrescriptionHistoryGroupQueryModel> grouped = new LinkedHashMap<>();
        // Pre：仅保留医生开立、已签发（有 prescription_no）的中医饮片行。
        for (MedicationRequest request : requests) {
            if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            if (!Boolean.TRUE.equals(request.getTcmFlag())) {
                continue;
            }
            if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(request.getGenerateSourceEnum())) {
                continue;
            }
            if (StringUtils.isEmpty(request.getPrescriptionNo())) {
                continue;
            }
            // 读侧排除退药逆向复制行，历史方计数与展示不含 T 前缀负向复制行（BR-ORD-030）
            if (request.getRefundMedicineId() != null) {
                continue;
            }
            String groupKey = request.getEncounterId() + "|" + request.getPrescriptionNo().trim();
            TcmHerbalPrescriptionHistoryGroupQueryModel group = grouped.get(groupKey);
            if (group == null) {
                group = new TcmHerbalPrescriptionHistoryGroupQueryModel();
                group.setPrescriptionNo(request.getPrescriptionNo().trim());
                group.setEncounterId(request.getEncounterId());
                group.setReqOpenedTime(this.resolveReqOpenedTimeWithAuthoredFallback(request));
                // 开立人随最早开立时间行落库 requester_id
                group.setRequesterId(request.getRequesterId());
                group.setLineCount(0);
                grouped.put(groupKey, group);
            }
            if (request.getMedicationId() != null) {
                group.getMedicationIds().add(request.getMedicationId());
            }
            group.setLineCount(group.getLineCount() + 1);
            // 方级开立时间取组内最早行（opened 空则回落 authored）；开立人随该最早行 requester_id 覆盖
            LocalDateTime lineOpenedTime = this.resolveReqOpenedTimeWithAuthoredFallback(request);
            if (lineOpenedTime != null) {
                if (group.getReqOpenedTime() == null || lineOpenedTime.isBefore(group.getReqOpenedTime())) {
                    group.setReqOpenedTime(lineOpenedTime);
                    // 更早开立行带 requester 时回写方级开立人
                    if (request.getRequesterId() != null) {
                        group.setRequesterId(request.getRequesterId());
                    }
                }
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 发药单筛选：在患者范围内解析状态落在集合内的药品申请主键（可排除指定就诊）。
     *
     * @param patientId          患者主键
     * @param excludeEncounterId 排除的就诊主键；{@code null} 表示患者下全部就诊
     * @param statusEnumsIn      请求状态 code 集合
     * @return 药品申请主键；入参无法构成范围时返回空列表
     */
    public List<Long> listMedicationRequestIdsByPatientScopeAndStatusIn(
        Long patientId,
        Long excludeEncounterId,
        Collection<String> statusEnumsIn) {
        if (patientId == null) {
            return Collections.emptyList();
        }
        List<String> statuses = this.normalizeStatusEnumIn(statusEnumsIn);
        if (statuses.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statuses));
            predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId));
            if (excludeEncounterId != null) {
                predicates.add(cb.notEqual(root.get(FieldNameConstants.ENCOUNTER_ID), excludeEncounterId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = this.medicationRequestRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 发药单筛选：在患者范围内解析指定开单科室的药品申请主键（可排除指定就诊）。
     *
     * @param patientId            患者主键
     * @param excludeEncounterId   排除的就诊主键；{@code null} 表示患者下全部就诊
     * @param orderingDepartmentId 开单科室组织主键（药嘱 {@code org_id}，{@code adm_organization.id}）
     * @return 药品申请主键；入参无法构成范围时返回空列表
     */
    public List<Long> listMedicationRequestIdsByPatientScopeAndOrgId(
        Long patientId,
        Long excludeEncounterId,
        Long orderingDepartmentId) {
        if (patientId == null || orderingDepartmentId == null) {
            return Collections.emptyList();
        }
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), orderingDepartmentId));
            if (excludeEncounterId != null) {
                predicates.add(cb.notEqual(root.get(FieldNameConstants.ENCOUNTER_ID), excludeEncounterId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = this.medicationRequestRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 发药单筛选：在单就诊范围内解析指定开单科室的药品申请主键。
     *
     * @param encounterId          就诊主键
     * @param orderingDepartmentId 开单科室组织主键（药嘱 {@code org_id}，{@code adm_organization.id}）
     * @return 药品申请主键；入参无法构成范围时返回空列表
     */
    public List<Long> listMedicationRequestIdsByEncounterAndOrgId(
        Long encounterId,
        Long orderingDepartmentId) {
        if (encounterId == null || orderingDepartmentId == null) {
            return Collections.emptyList();
        }
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), encounterId));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), orderingDepartmentId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = this.medicationRequestRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 发药单筛选：在多就诊范围内解析指定开单科室的药品申请主键。
     *
     * @param encounterIdsScope    就诊主键集合
     * @param orderingDepartmentId 开单科室组织主键（药嘱 {@code org_id}，{@code adm_organization.id}）
     * @return 药品申请主键；入参无法构成范围时返回空列表
     */
    public List<Long> listMedicationRequestIdsByEncounterIdsAndOrgId(
        Collection<Long> encounterIdsScope,
        Long orderingDepartmentId) {
        if (encounterIdsScope == null || encounterIdsScope.isEmpty() || orderingDepartmentId == null) {
            return Collections.emptyList();
        }
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIdsScope));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), orderingDepartmentId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = this.medicationRequestRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 报表开立科室筛：按药嘱 {@code org_id} 解析药品申请主键（不限就诊；仅本聚合）。
     *
     * @param orderingDepartmentId 开立科室组织主键（{@code adm_organization.id}）
     * @return 药品申请主键；入参空时返回空列表
     */
    public List<Long> listMedicationRequestIdsByOrgId(Long orderingDepartmentId) {
        if (orderingDepartmentId == null) {
            return Collections.emptyList();
        }
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), orderingDepartmentId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = this.medicationRequestRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按药品申请主键批量加载列表展示模型（发药 slip 跨就诊装配复用）。
     *
     * @param ids 药品申请主键集合
     * @return 列表模型；无命中时返回空列表
     */
    public List<MedicationRequestListQueryModel> listMedicationRequestListModelsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<MedicationRequest> requests = this.listByIds(ids);
        List<MedicationRequestListQueryModel> result = new ArrayList<>();
        for (MedicationRequest request : requests) {
            if (request != null) {
                result.add(this.toListModel(request));
            }
        }
        return result;
    }

    /**
     * 发药单筛选：在就诊范围内解析状态落在集合内的药品申请主键。
     *
     * @param encounterId        单就诊主键；与 {@code encounterIdsScope} 二选一优先本字段
     * @param encounterIdsScope  多就诊主键；{@code encounterId} 为空时使用
     * @param statusEnumsIn      请求状态 code 集合
     * @return 药品申请主键；入参无法构成范围时返回空列表
     */
    public List<Long> listMedicationRequestIdsByEncounterScopeAndStatusIn(
        Long encounterId,
        Collection<Long> encounterIdsScope,
        Collection<String> statusEnumsIn) {
        List<String> statuses = this.normalizeStatusEnumIn(statusEnumsIn);
        if (statuses.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statuses));
            if (encounterId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), encounterId));
            } else if (encounterIdsScope != null && !encounterIdsScope.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIdsScope));
            } else {
                return cb.equal(cb.literal(1), cb.literal(0));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = this.medicationRequestRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 状态 in 条件归一：trim 非空 code，空集合返回空列表。
     *
     * @param statusEnumsIn 原始状态 code 集合
     * @return trim 后的状态列表
     */
    private List<String> normalizeStatusEnumIn(Collection<String> statusEnumsIn) {
        List<String> statuses = new ArrayList<>();
        if (statusEnumsIn == null) {
            return statuses;
        }
        for (String raw : statusEnumsIn) {
            if (StringUtils.isNotEmpty(raw)) {
                statuses.add(raw.trim());
            }
        }
        return statuses;
    }

    /**
     * 列表行映射（医生站与其它列表复用）。
     * <p>被 listMedicationRequestsByEncounter 与 listMedicationRequestsByEncounterForDoctorStation 调用，
     * 将 MedicationRequest 实体转换为列表展示所需的字段子集。</p>
     *
     * @param request 药品申请实体
     * @return 列表模型
     */
    private MedicationRequestListQueryModel toListModel(MedicationRequest request) {
        MedicationRequestListQueryModel model = new MedicationRequestListQueryModel();
        model.setId(request.getId());
        model.setBusNo(request.getBusNo());
        model.setPrescriptionNo(request.getPrescriptionNo());
        model.setStatusEnum(request.getStatusEnum());
        model.setRefundMedicineId(request.getRefundMedicineId());
        model.setGroupId(request.getGroupId());
        // 组套标识：护士站成组执行绑定耗材派生可与主药嘱 package_id 对齐
        model.setPackageId(request.getPackageId());
        model.setEncounterId(request.getEncounterId());
        model.setPatientId(request.getPatientId());
        model.setMedicationId(request.getMedicationId());
        model.setGenerateSourceEnum(request.getGenerateSourceEnum());
        model.setRecorderId(request.getRecorderId());
        model.setRequesterId(request.getRequesterId());
        model.setReqOpenedTime(request.getReqOpenedTime());
        model.setReqAuthoredTime(request.getReqAuthoredTime());
        model.setSortNumber(request.getSortNumber());
        model.setQuantity(request.getQuantity());
        model.setUnitCode(request.getUnitCode());
        model.setMethodCode(request.getMethodCode());
        model.setRateCode(request.getRateCode());
        model.setDose(request.getDose());
        model.setDoseUnitCode(request.getDoseUnitCode());
        // 首日/首次用量：护士执行首次槽换量与医生站列表回显（BR-IP-ORD-030）
        model.setFirstDose(request.getFirstDose());
        model.setFirstDoseUnitCode(request.getFirstDoseUnitCode());
        model.setLotNumber(request.getLotNumber());
        model.setDispensePerDuration(request.getDispensePerDuration());
        model.setOrgId(request.getOrgId());
        model.setPerformLocation(request.getPerformLocation());
        model.setEncounterDiagnosisIds(request.getEncounterDiagnosisIds());
        model.setRemark(request.getRemark());
        model.setContentJson(request.getContentJson());
        model.setRxTypeCode(request.getRxTypeCode());
        model.setContractId(request.getContractId());
        model.setDosageInstruction(request.getDosageInstruction());
        model.setSpeedCode(request.getSpeedCode());
        model.setChineseHerbsDoseQuantity(request.getChineseHerbsDoseQuantity());
        model.setSufferingFlag(request.getSufferingFlag());
        model.setSkinTestFlag(request.getSkinTestFlag());
        model.setInfusionFlag(request.getInfusionFlag());
        model.setBasedOnTable(request.getBasedOnTable());
        model.setBasedOnId(request.getBasedOnId());
        model.setPerformerCheckId(request.getPerformerCheckId());
        model.setCheckTime(request.getCheckTime());
        model.setEffectiveDoseStart(request.getEffectiveDoseStart());
        model.setEffectiveDoseEnd(request.getEffectiveDoseEnd());
        model.setStopTime(request.getStopTime());
        model.setStopperId(request.getStopperId());
        model.setStopPerformerCheckId(request.getStopPerformerCheckId());
        model.setStopCheckTime(request.getStopCheckTime());
        model.setTcmFlag(request.getTcmFlag());
        model.setTherapyEnum(request.getTherapyEnum());
        return model;
    }

    /**
     * 医生站历史医嘱：在患者范围内收集存在医生开立药嘱的就诊主键（排除当前就诊）。
     *
     * @param patientId          患者主键
     * @param excludeEncounterId 当前就诊（排除锚点）
     * @return 历史就诊主键（去重、无序）
     */
    public List<Long> listDistinctEncounterIdsByPatientForDoctorStation(Long patientId, Long excludeEncounterId) {
        // 前置：门控主键缺失时无法收窄历史范围，直接返回空集。
        if (patientId == null || excludeEncounterId == null) {
            return Collections.emptyList();
        }
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.ENCOUNTER_ID), excludeEncounterId));
            predicates.add(cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), doctorSource));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = medicationRequestRepository.findAll(specification);
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // Loop：从医生开立药嘱行提取就诊 id，LinkedHashSet 保序去重。
        for (MedicationRequest row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        return new ArrayList<>(encounterIds);
    }

    /**
     * 查询患者历史药品申请（排除当前就诊）。
     *
     * @param patientId          患者标识
     * @param currentEncounterId 当前就诊标识
     * @return 历史药品申请集合
     */
    public List<MedicationRequestHistoryQueryModel> listMedicationRequestHistoryByPatient(Long patientId, Long currentEncounterId) {
        List<MedicationRequest> requests = medicationRequestRepository.findByPatientIdAndEncounterIdNot(
            patientId, currentEncounterId);
        List<MedicationRequestHistoryQueryModel> result = new ArrayList<>();
        // Loop：遍历患者历史药品申请（排除当前就诊），逐条装配轻量历史模型
        for (MedicationRequest request : requests) {
            MedicationRequestHistoryQueryModel model = new MedicationRequestHistoryQueryModel();
            model.setId(request.getId());
            model.setEncounterId(request.getEncounterId());
            model.setMedicationId(request.getMedicationId());
            model.setStatusEnum(request.getStatusEnum());
            model.setCreatedAt(request.getCreatedAt());
            result.add(model);
        }
        return result;
    }

    /**
     * 校验药品申请是否允许删除。
     * <p>业务规则：仅草稿态允许删除，已发送或已完成状态禁止删除。</p>
     *
     * @param id 药品申请标识
     * @return 是否允许删除
     */
    public boolean checkMedicationRequestDeletable(Long id) {
        MedicationRequest request = this.mustGet(id);
        return RequestStatus.DRAFT.getCode().equals(request.getStatusEnum());
    }

    /**
     * 校验药品申请是否允许签退。
     * <p>业务规则：仅已发送态允许签退，草稿或已完成状态禁止签退。</p>
     *
     * @param id 药品申请标识
     * @return 是否允许签退
     */
    public boolean checkMedicationRequestSignOffAllowed(Long id) {
        MedicationRequest request = this.mustGet(id);
        return RequestStatus.ACTIVE.getCode().equals(request.getStatusEnum());
    }

    /**
     * 按原药品申请主键批量查询关联退药/负向申请（DM-MED-003 §6.2.6；AS-OP-FIN-003 IF-REF-04）。
     *
     * @param refundMedicineIds 原药品申请主键集合（{@code refund_medicine_id} 指向方）
     * @return 命中关联字段的申请行；入参为空时返回空列表
     */
    public List<MedicationRequest> listByRefundMedicineIdIn(Collection<Long> refundMedicineIds) {
        if (refundMedicineIds == null || refundMedicineIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : refundMedicineIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return this.medicationRequestRepository.findByRefundMedicineIdIn(ids);
    }

    /**
     * 按就诊主键集合与请求状态批量查询药品申请（只读；供应用层批量计数等编排）。
     *
     * @param encounterIds 就诊主键集合
     * @param statusEnum   请求状态 {@link RequestStatus#getCode()}
     * @return 药品申请列表；入参为空时返回空列表
     * @throws BusinessException statusEnum 为空
     */
    public List<MedicationRequest> listByEncounterIdsAndStatusEnum(
        Collection<Long> encounterIds,
        String statusEnum) {
        Collection<Long> idSet = this.distinctNonNullEncounterIds(encounterIds);
        if (idSet.isEmpty()) {
            return Collections.emptyList();
        }
        if (StringUtils.isEmpty(statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "按就诊批量查询时 statusEnum 不能为空");
        }
        return medicationRequestRepository.findByEncounterIdInAndStatusEnum(idSet, statusEnum.trim());
    }

    /**
     * 住院医生站药品医嘱列表（替代 OrderInfo RM Q-ORD-007 药嘱子集）。
     */
    public List<MedicationRequestListQueryModel> listInpatientDoctorStationOrders(Long encounterId, String therapyEnum) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "住院医生站药品医嘱列表查询时 encounterId 不能为空");
        }
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(encounterId, List.of(encounterId), null, therapyEnum));
    }

    /**
     * 住院护士站校对药品医嘱列表（替代 OrderInfo RM Q-IP-AUD-001 药嘱子集）。
     */
    public List<MedicationRequestListQueryModel> listInpatientNurseStationAuditOrders(
        List<Long> encounterIds, List<String> statusEnumIn, String therapyEnum) {
        this.validateEncounterIdsAndStatusIn(encounterIds, statusEnumIn, "住院护士站药品校对列表");
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(null, encounterIds, statusEnumIn, therapyEnum));
    }

    /**
     * 住院护士站执行药品医嘱列表（替代 OrderInfo RM Q-IP-EXE-001 药嘱子集）。
     *
     * <p>状态含 {@link RequestStatus#VERIFIED}、{@link RequestStatus#STOPPED}（停嘱后仍在执行列表展示）。</p>
     */
    public List<MedicationRequestListQueryModel> listInpatientNurseStationExecuteOrders(
        List<Long> encounterIds, String therapyEnum) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "住院护士站药品执行列表查询时 encounterIds 不能为空");
        }
        List<String> executeStatuses = List.of(
            RequestStatus.VERIFIED.getCode(),
            RequestStatus.STOPPED.getCode());
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(null, encounterIds, executeStatuses, therapyEnum));
    }

    /**
     * 住院护士站补费父药品医嘱列表（替代 OrderInfo RM Q-IP-PRC-002 药嘱子集）。
     */
    public List<MedicationRequestListQueryModel> listInpatientNurseStationSupplementOrders(
        List<Long> encounterIds, String therapyEnum) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "住院护士站补费父药品列表查询时 encounterIds 不能为空");
        }
        List<String> parentStatuses = List.of(RequestStatus.VERIFIED.getCode(), RequestStatus.STOPPED.getCode());
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(null, encounterIds, parentStatuses, therapyEnum));
    }

    /**
     * 按主键加载药品申请列表行（供按表路由）。
     */
    public MedicationRequestListQueryModel getListModelById(Long id) {
        if (id == null) {
            return null;
        }
        MedicationRequest request = medicationRequestRepository.findById(id).orElse(null);
        if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
            return null;
        }
        return this.toListModel(request);
    }

    /**
     * 按主键强制加载药品申请列表行。
     */
    public MedicationRequestListQueryModel mustGetListModelById(Long id) {
        MedicationRequest request = this.mustGet(id);
        return this.toListModel(request);
    }

    /**
     * 住院医生开立药嘱查询：固定 generate_source=DOCTOR_PRESCRIPTION，可按单就诊/多就诊与状态、therapy 过滤。
     *
     * @param singleEncounterId 单就诊收窄（非空时忽略 encounterIds 列表语义）
     * @param encounterIds      多就诊 in 条件
     * @param statusEnumIn      状态 in；可 null 表示不限
     * @param therapyEnum       长临嘱 code；可空
     * @return 实体列表（按 created_at 降序）
     */
    private List<MedicationRequest> findInpatientDoctorPrescriptionOrders(
        Long singleEncounterId,
        List<Long> encounterIds,
        List<String> statusEnumIn,
        String therapyEnum) {
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(
                root.get(FieldNameConstants.GENERATE_SOURCE_ENUM),
                GenerateSource.DOCTOR_PRESCRIPTION.getCode()));
            if (singleEncounterId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), singleEncounterId));
            } else if (encounterIds != null && !encounterIds.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIds));
            }
            if (statusEnumIn != null && !statusEnumIn.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusEnumIn));
            }
            if (StringUtils.isNotEmpty(therapyEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.THERAPY_ENUM), therapyEnum));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        return medicationRequestRepository.findAll(specification, sort);
    }

    /**
     * 药品申请实体批量投影为列表 QueryModel。
     *
     * @param requests 实体列表
     * @return 列表模型
     */
    private List<MedicationRequestListQueryModel> mapListModels(List<MedicationRequest> requests) {
        List<MedicationRequestListQueryModel> result = new ArrayList<>();
        for (MedicationRequest request : requests) {
            result.add(this.toListModel(request));
        }
        return result;
    }

    /**
     * 住院护士站等多就诊查询前置：encounterIds 与 statusEnumIn 均不可空。
     *
     * @param encounterIds  就诊主键列表
     * @param statusEnumIn  状态 in 列表
     * @param sceneLabel    场景标签（拼入异常文案）
     * @throws BusinessException MEDICATION_REQUEST_INVALID_ARGUMENT
     */
    private void validateEncounterIdsAndStatusIn(
        List<Long> encounterIds, List<String> statusEnumIn, String sceneLabel) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                sceneLabel + "查询时 encounterIds 不能为空");
        }
        if (statusEnumIn == null || statusEnumIn.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                sceneLabel + "查询时 statusEnumIn 不能为空");
        }
    }

    /**
     * 就诊主键去重（批量查询入参归一化）。
     *
     * @param encounterIds 原始就诊主键集合
     * @return 非空去重集合
     */
    private Collection<Long> distinctNonNullEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        if (encounterIds == null) {
            return idSet;
        }
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                idSet.add(encounterId);
            }
        }
        return idSet;
    }

    /**
     * 按主键加载药品申请聚合根，供详情与门控复用。
     * <p>被 loadMedicationRequestById、checkMedicationRequestDeletable、checkMedicationRequestSignOffAllowed 调用，
     * 统一封装药品申请存在性校验逻辑。</p>
     *
     * @param id 药品申请标识
     * @return 药品申请实体
     * @throws BusinessException 申请不存在时抛出
     */
    private MedicationRequest mustGet(Long id) {
        return medicationRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在"));
    }

    /**
     * 申请详情字段映射。
     * <p>被 loadMedicationRequestById 调用，将 MedicationRequest 实体转换为详情展示所需的字段子集。</p>
     *
     * @param request 药品申请实体
     * @return 详情模型
     */
    private MedicationRequestDetailQueryModel toDetailModel(MedicationRequest request) {
        MedicationRequestDetailQueryModel model = new MedicationRequestDetailQueryModel();
        model.setId(request.getId());
        model.setStatusEnum(request.getStatusEnum());
        model.setTherapyEnum(request.getTherapyEnum());
        model.setQuantity(request.getQuantity());
        model.setExecuteNum(request.getExecuteNum());
        model.setUnitCode(request.getUnitCode());
        return model;
    }

    /**
     * 将扁平行中药饮片列表按方聚合为 {@link TcmHerbalPrescriptionGroupQueryModel}。
     *
     * @param tcmLines 已过滤 tcm_flag 的列表行
     * @return 按方分组结果（保持插入顺序）
     */
    private List<TcmHerbalPrescriptionGroupQueryModel> groupTcmHerbalPrescriptionLines(
        List<MedicationRequestListQueryModel> tcmLines) {
        Map<String, TcmHerbalPrescriptionGroupQueryModel> grouped = new LinkedHashMap<>();
        for (MedicationRequestListQueryModel line : tcmLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            String groupKey = this.resolveTcmPrescriptionGroupKey(line);
            TcmHerbalPrescriptionGroupQueryModel group = grouped.get(groupKey);
            if (group == null) {
                group = new TcmHerbalPrescriptionGroupQueryModel();
                group.setPrescriptionGroupKey(this.readTcmContentJsonTextField(line.getContentJson(), "prescriptionGroupKey"));
                group.setPrescriptionNo(line.getPrescriptionNo());
                group.setTcmSyndromeGroup(this.readTcmContentJsonTextField(line.getContentJson(), "tcmSyndromeGroup"));
                group.setChineseHerbsDoseQuantity(line.getChineseHerbsDoseQuantity());
                group.setSufferingFlag(line.getSufferingFlag());
                group.setMethodCode(line.getMethodCode());
                // 方头用药频次取组内首行 rate_code，与 methodCode 同源
                group.setRateCode(line.getRateCode());
                group.setDispensePerDuration(line.getDispensePerDuration());
                group.setRemark(line.getRemark());
                group.setRxTypeCode(line.getRxTypeCode());
                group.setTherapyEnum(line.getTherapyEnum());
                group.setStatusEnum(line.getStatusEnum());
                group.setReqOpenedTime(this.resolveReqOpenedTimeWithAuthoredFallback(line));
                grouped.put(groupKey, group);
            }
            group.getLines().add(line);
            // 同方后续行补齐方头处方号/方标识（签退后新增行可能尚无 prescription_no）
            if (StringUtils.isEmpty(group.getPrescriptionNo()) && StringUtils.isNotEmpty(line.getPrescriptionNo())) {
                group.setPrescriptionNo(line.getPrescriptionNo());
            }
            if (StringUtils.isEmpty(group.getPrescriptionGroupKey())) {
                String lineGroupKey = this.readTcmContentJsonTextField(line.getContentJson(), "prescriptionGroupKey");
                if (StringUtils.isNotEmpty(lineGroupKey)) {
                    group.setPrescriptionGroupKey(lineGroupKey);
                }
            }
            group.setStatusEnum(this.mergeTcmPrescriptionStatusEnum(group.getStatusEnum(), line.getStatusEnum()));
            LocalDateTime lineOpenedTime = this.resolveReqOpenedTimeWithAuthoredFallback(line);
            if (lineOpenedTime != null) {
                if (group.getReqOpenedTime() == null || lineOpenedTime.isBefore(group.getReqOpenedTime())) {
                    group.setReqOpenedTime(lineOpenedTime);
                }
            }
        }
        List<TcmHerbalPrescriptionGroupQueryModel> result = new ArrayList<>(grouped.values());
        // Asm：方内行按 sortNumber、requestId 稳定排序，避免读侧随库表物理顺序漂移
        for (TcmHerbalPrescriptionGroupQueryModel group : result) {
            group.getLines().sort(Comparator
                .comparing(MedicationRequestListQueryModel::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MedicationRequestListQueryModel::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return result;
    }

    /**
     * 解析单行所属处方分组键。
     *
     * <p>优先 {@code content_json.prescriptionGroupKey}：签退回草稿后行仍保留 {@code prescription_no}，
     * 若仍按处方号优先分组，同方新建（尚无处方号）的行会被拆成另一张方。</p>
     *
     * @param line 中药列表行
     * @return 聚合用内部键（KEY: / RX: / ROW: 前缀）
     */
    private String resolveTcmPrescriptionGroupKey(MedicationRequestListQueryModel line) {
        String prescriptionGroupKey = this.readTcmContentJsonTextField(line.getContentJson(), "prescriptionGroupKey");
        // 有前端方标识时统一按 key 归并（签发/签退后同方续开不拆方）
        if (StringUtils.isNotEmpty(prescriptionGroupKey)) {
            return "KEY:" + prescriptionGroupKey.trim();
        }
        if (StringUtils.isNotEmpty(line.getPrescriptionNo())) {
            return "RX:" + line.getPrescriptionNo().trim();
        }
        return "ROW:" + line.getId();
    }

    /**
     * 门诊已签发处方药品行分页（AS-OP-PHA-005 · IF-OPQ-02 / DM-MED-003 §6.2.21）。
     *
     * <p>已签发口径：{@code prescription_no} 非空 <b>且</b> {@code status_enum} 为 {@link RequestStatus#ACTIVE}
     * 或 {@link RequestStatus#COMPLETED}（排除仅被重分方写了 CP 号、仍为 {@link RequestStatus#DRAFT} 的行）。
     * 开立时间轴 {@code req_opened_time}（空回落 {@code req_authored_time}）；正方与退方均纳入。
     * 可选 {@link OutpatientSignedPrescriptionDrugLinePageQuery#getPrescriptionNo()} 精确匹配供详情整方重载。
     * 跨聚合筛选项须由应用层预写 {@link OutpatientSignedPrescriptionDrugLinePageQuery} 的 {@code resolved*} 字段；
     * 含输液 YES/NO 在同聚合内按注射目录主键解析处方号后再展开整方行（B.6.4，不跨根 EXISTS）。</p>
     *
     * @param query 分页与预解析筛选；{@code null} 时按空条件分页（仍要求已签发）
     * @return 列表读模型页；无匹配时为空页
     */
    public Page<MedicationRequestListQueryModel> pageOutpatientSignedPrescriptionDrugLines(
        OutpatientSignedPrescriptionDrugLinePageQuery query) {
        OutpatientSignedPrescriptionDrugLinePageQuery effectiveQuery = query;
        // 入参空时仍走已签发基础口径，避免 NPE
        if (effectiveQuery == null) {
            effectiveQuery = new OutpatientSignedPrescriptionDrugLinePageQuery();
        }
        int pageNo = effectiveQuery.springPageIndex();
        int pageSize = effectiveQuery.getBoundedPageSize();
        // 业务排序主轴：开立时刻；同开立再按方号/行序/主键稳定序（签发仅追溯，不作次键）。
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.REQ_OPENED_TIME),
            Sort.Order.asc(FieldNameConstants.PRESCRIPTION_NO),
            Sort.Order.asc(FieldNameConstants.SORT_NUMBER),
            Sort.Order.asc(FieldNameConstants.ID));
        PageRequest pageable = PageRequest.of(pageNo, pageSize, sort);
        Specification<MedicationRequest> specification =
            this.buildOutpatientSignedPrescriptionDrugLineSpecification(effectiveQuery);
        Page<MedicationRequest> page = medicationRequestRepository.findAll(specification, pageable);
        List<MedicationRequestListQueryModel> models = new ArrayList<>();
        // Loop：页内实体转列表读模型，供应用层再装配收费/发药/目录展示
        for (MedicationRequest row : page.getContent()) {
            models.add(this.toListModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 门诊已签发处方筛选窗内去重汇总（页脚总处方量 / 就诊人数）。
     *
     * <p>规约与 {@link #pageOutpatientSignedPrescriptionDrugLines} 完全一致；
     * 用 {@code COUNT(DISTINCT prescription_no)} / {@code COUNT(DISTINCT encounter_id)}，
     * 禁止全窗物化明细再内存去重。</p>
     *
     * @param query 与分页相同的预解析筛选；{@code null} 时按空条件（仍要求已签发）
     * @return 去重计数；无命中时为 0
     */
    public OutpatientSignedPrescriptionSummaryCountQueryModel countOutpatientSignedPrescriptionSummary(
        OutpatientSignedPrescriptionDrugLinePageQuery query) {
        OutpatientSignedPrescriptionDrugLinePageQuery effectiveQuery = query;
        // 入参空时仍走已签发基础口径，避免 NPE
        if (effectiveQuery == null) {
            effectiveQuery = new OutpatientSignedPrescriptionDrugLinePageQuery();
        }
        Specification<MedicationRequest> specification =
            this.buildOutpatientSignedPrescriptionDrugLineSpecification(effectiveQuery);
        OutpatientSignedPrescriptionSummaryCountQueryModel model =
            new OutpatientSignedPrescriptionSummaryCountQueryModel();
        // Asm：处方号去重 → 总处方量
        long prescriptionCount =
            this.countDistinctMedicationRequestField(specification, FieldNameConstants.PRESCRIPTION_NO);
        // Asm：就诊主键去重 → 就诊人数
        long visitPatientCount =
            this.countDistinctMedicationRequestField(specification, FieldNameConstants.ENCOUNTER_ID);
        model.setPrescriptionCount(toBoundedIntCount(prescriptionCount));
        model.setVisitPatientCount(toBoundedIntCount(visitPatientCount));
        return model;
    }

    /**
     * 读侧 Criteria 按指定字段去重计数。
     *
     * @param specification JPA 规约
     * @param fieldName     实体属性名（FieldNameConstants）
     * @return 不重复数量
     */
    private long countDistinctMedicationRequestField(
        Specification<MedicationRequest> specification,
        String fieldName) {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<MedicationRequest> root = cq.from(MedicationRequest.class);
        cq.select(cb.countDistinct(root.get(fieldName)));
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, cq, cb);
            if (predicate != null) {
                cq.where(predicate);
            }
        }
        Long totalRow = this.entityManager.createQuery(cq).getSingleResult();
        if (totalRow == null) {
            return 0L;
        }
        return totalRow.longValue();
    }

    /**
     * long 计数收敛为 Integer 出参上界（避免溢出；业务量远低于 Integer.MAX_VALUE）。
     *
     * @param count 原始计数
     * @return 非负且不超过 Integer.MAX_VALUE
     */
    private static int toBoundedIntCount(long count) {
        if (count <= 0L) {
            return 0;
        }
        if (count >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }

    /**
     * 组装门诊已签发处方药品行分页规约（仅本聚合根；跨聚合条件已预解析为 IN）。
     *
     * @param query 有效分页入参
     * @return JPA Specification
     */
    private Specification<MedicationRequest> buildOutpatientSignedPrescriptionDrugLineSpecification(
        OutpatientSignedPrescriptionDrugLinePageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 已签发：处方号非空（正方 CP… / 退方 TCP…）
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.PRESCRIPTION_NO)));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.PRESCRIPTION_NO), ""));
            // 须真正签发完成：排除仅重分方写了 CP、医嘱仍为 DRAFT 的行（签退后回 DRAFT 也不入列表）
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                RequestStatus.ACTIVE.getCode(),
                RequestStatus.COMPLETED.getCode()));
            this.applyReqOpenedTimeWindow(predicates, root, cb, query);
            String rxTypeCode = StringUtils.trimToNull(query.getRxTypeCode());
            // 处方类别精确匹配（术语 355）
            if (rxTypeCode != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.RX_TYPE_CODE), rxTypeCode));
            }
            String prescriptionNo = StringUtils.trimToNull(query.getPrescriptionNo());
            // 处方号精确匹配：详情整方重载；与其它筛 AND
            if (prescriptionNo != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PRESCRIPTION_NO), prescriptionNo));
            }
            // 开单科室：本聚合 org_id 等值（BR-MRQ-029，禁止用 perform_location）
            if (query.getOrderingDepartmentId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), query.getOrderingDepartmentId()));
            }
            this.applyResolvedIdInOrReject(
                predicates, root, cb, FieldNameConstants.ID, query.getResolvedMedicationRequestIdsForChargeStatus());
            this.applyResolvedIdInOrReject(
                predicates, root, cb, FieldNameConstants.ID, query.getResolvedMedicationRequestIdsForDispenseStatus());
            this.applyResolvedIdInOrReject(
                predicates, root, cb, FieldNameConstants.ID, query.getResolvedMedicationRequestIdsForMedType());
            this.applyResolvedIdInOrReject(
                predicates, root, cb, FieldNameConstants.CONTRACT_ID, query.getResolvedContractIdsForCategory());
            this.applyResolvedIdInOrReject(
                predicates, root, cb, FieldNameConstants.ENCOUNTER_ID, query.getResolvedEncounterIds());
            this.applyInfusionPrescriptionFilter(predicates, root, cb, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 开立时间闭区间；轴为 {@code COALESCE(req_opened_time, req_authored_time)}，有窗时排除轴为空。
     */
    private void applyReqOpenedTimeWindow(
        List<Predicate> predicates,
        Root<MedicationRequest> root,
        CriteriaBuilder cb,
        OutpatientSignedPrescriptionDrugLinePageQuery query) {
        jakarta.persistence.criteria.Expression<LocalDateTime> openedAxis = cb.coalesce(
            root.get(FieldNameConstants.REQ_OPENED_TIME),
            root.get(FieldNameConstants.REQ_AUTHORED_TIME));
        if (query.getStartDate() != null || query.getEndDate() != null) {
            predicates.add(cb.isNotNull(openedAxis));
        }
        if (query.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(openedAxis, query.getStartDate()));
        }
        if (query.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(openedAxis, query.getEndDate()));
        }
    }

    /**
     * 单行开立时刻：优先 {@code reqOpenedTime}，空回落 {@code reqAuthoredTime}（方级 min 聚合用）。
     *
     * @param request 药品申请实体
     * @return 开立轴时刻；皆空时 null
     */
    private LocalDateTime resolveReqOpenedTimeWithAuthoredFallback(MedicationRequest request) {
        if (request == null) {
            return null;
        }
        // 方级聚合主轴：开立列优先，空则回落签发追溯列。
        if (request.getReqOpenedTime() != null) {
            return request.getReqOpenedTime();
        }
        return request.getReqAuthoredTime();
    }

    /**
     * 列表读模型开立时刻：优先 {@code reqOpenedTime}，空回落 {@code reqAuthoredTime}。
     *
     * @param line 药品申请列表读模型
     * @return 开立轴时刻；皆空时 null
     */
    private LocalDateTime resolveReqOpenedTimeWithAuthoredFallback(MedicationRequestListQueryModel line) {
        if (line == null) {
            return null;
        }
        // 方级聚合主轴：开立列优先，空则回落签发追溯列。
        if (line.getReqOpenedTime() != null) {
            return line.getReqOpenedTime();
        }
        return line.getReqAuthoredTime();
    }

    /**
     * 预解析主键集合：{@code null} 不限；空列表拒查；非空 {@code IN}。
     */
    private void applyResolvedIdInOrReject(
        List<Predicate> predicates,
        Root<MedicationRequest> root,
        CriteriaBuilder cb,
        String fieldName,
        List<Long> resolvedIds) {
        if (resolvedIds == null) {
            return;
        }
        // 预解析无命中：返回空结果，避免误扫全表
        if (resolvedIds.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(root.get(fieldName).in(resolvedIds));
    }

    /**
     * 方级含输液筛：同聚合先按注射目录主键收集处方号，再展开整方行（不跨 {@code MedicationDefinition} 根）。
     */
    private void applyInfusionPrescriptionFilter(
        List<Predicate> predicates,
        Root<MedicationRequest> root,
        CriteriaBuilder cb,
        OutpatientSignedPrescriptionDrugLinePageQuery query) {
        String infusionFilter = StringUtils.trimToNull(query.getInfusionFilterCode());
        if (infusionFilter == null
            || OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_ALL.equals(infusionFilter)) {
            return;
        }
        List<Long> injectMedicationIds = query.getResolvedMedicationIdsForInjectFlag();
        // YES/NO 须带注射目录主键；缺失则拒查，避免口径漂移
        if (injectMedicationIds == null || injectMedicationIds.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        Set<String> infusionPrescriptionNos = this.listSignedPrescriptionNosByMedicationIdsInWindow(
            injectMedicationIds, query.getStartDate(), query.getEndDate());
        if (OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_YES.equals(infusionFilter)) {
            // 含输液：返回命中处方号下全部明细行
            if (infusionPrescriptionNos.isEmpty()) {
                predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
                return;
            }
            predicates.add(root.get(FieldNameConstants.PRESCRIPTION_NO).in(infusionPrescriptionNos));
            return;
        }
        if (OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_NO.equals(infusionFilter)) {
            // 不含输液：排除含注射目录药品的处方号；若集合空则无需 NOT IN
            if (infusionPrescriptionNos.isEmpty()) {
                return;
            }
            predicates.add(cb.not(root.get(FieldNameConstants.PRESCRIPTION_NO).in(infusionPrescriptionNos)));
            return;
        }
        // 未知筛码：拒查，防止静默忽略错误条件
        predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
    }

    /**
     * 同聚合：在开立时间窗内，收集命中给定药品定义主键的已签发处方号集合。
     *
     * <p>时间轴与主列表一致：{@code COALESCE(req_opened_time, req_authored_time)}，签发仅作空开立回落。</p>
     *
     * @param medicationIds 目录主键
     * @param startDate     开立起（可空）
     * @param endDate       开立止（可空）
     * @return 处方号集合；无命中为空集
     */
    private Set<String> listSignedPrescriptionNosByMedicationIdsInWindow(
        Collection<Long> medicationIds,
        java.time.LocalDateTime startDate,
        java.time.LocalDateTime endDate) {
        Specification<MedicationRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.PRESCRIPTION_NO)));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.PRESCRIPTION_NO), ""));
            predicates.add(root.get(FieldNameConstants.MEDICATION_ID).in(medicationIds));
            // 含输液方级筛与主分页同一开立轴，避免改开立后 YES/NO 与列表窗不一致。
            jakarta.persistence.criteria.Expression<LocalDateTime> openedAxis = cb.coalesce(
                root.get(FieldNameConstants.REQ_OPENED_TIME),
                root.get(FieldNameConstants.REQ_AUTHORED_TIME));
            if (startDate != null || endDate != null) {
                predicates.add(cb.isNotNull(openedAxis));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(openedAxis, startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(openedAxis, endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationRequest> rows = medicationRequestRepository.findAll(specification);
        Set<String> prescriptionNos = new HashSet<>();
        // Loop：从注射目录命中的已签发行收集处方号，供方级 YES/NO 展开
        for (MedicationRequest row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getPrescriptionNo())) {
                prescriptionNos.add(row.getPrescriptionNo());
            }
        }
        return prescriptionNos;
    }

    /**
     * 合并方内多行状态：ACTIVE 优先于 DRAFT，COMPLETED 优先于 ACTIVE。
     */
    private String mergeTcmPrescriptionStatusEnum(String current, String incoming) {
        if (StringUtils.isEmpty(current)) {
            return incoming;
        }
        if (StringUtils.isEmpty(incoming)) {
            return current;
        }
        if (RequestStatus.COMPLETED.getCode().equals(current) || RequestStatus.COMPLETED.getCode().equals(incoming)) {
            return RequestStatus.COMPLETED.getCode();
        }
        if (RequestStatus.ACTIVE.getCode().equals(current) || RequestStatus.ACTIVE.getCode().equals(incoming)) {
            return RequestStatus.ACTIVE.getCode();
        }
        return current;
    }

    /**
     * 从中药 {@code content_json} 读取顶层文本字段（如 {@code prescriptionGroupKey}，该 key 不落独立库列）。
     *
     * @param contentJson 扩展 JSON
     * @param fieldName   字段名
     * @return 非空文本；解析失败或未命中时返回 null
     */
    private String readTcmContentJsonTextField(String contentJson, String fieldName) {
        if (StringUtils.isEmpty(contentJson) || StringUtils.isEmpty(fieldName)) {
            return null;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(contentJson);
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                return null;
            }
            String text = node.asText(null);
            if (StringUtils.isEmpty(text)) {
                return null;
            }
            return text.trim();
        } catch (Exception ignored) {
            // 读侧解析失败时返回 null，由 resolveTcmPrescriptionGroupKey 回落处方号或行 id。
            return null;
        }
    }

    /**
     * 统计未软删药品请求中 {@code encounter_diagnosis_ids} 引用指定就诊诊断行的条数。
     *
     * @param encounterDiagnosisId 就诊诊断行主键
     * @return 引用条数；入参空时 0
     */
    public int countReferencingEncounterDiagnosisId(Long encounterDiagnosisId) {
        if (encounterDiagnosisId == null) {
            return 0;
        }
        Specification<MedicationRequest> specification = (root, query, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            EncounterDiagnosisIdsPredicates.containsEncounterDiagnosisId(
                cb, root.get(FieldNameConstants.ENCOUNTER_DIAGNOSIS_IDS), encounterDiagnosisId)
        );
        long count = medicationRequestRepository.count(specification);
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }

    /**
     * 列出未软删药品请求中 {@code encounter_diagnosis_ids} 引用任一给定就诊诊断行的主键（AS-OP-PHA-005 医保类别筛预解析）。
     *
     * @param encounterDiagnosisIds 就诊诊断行主键集合
     * @return 药品申请主键去重列表；入参空时返回空列表
     */
    public List<Long> listIdsReferencingAnyEncounterDiagnosisId(Collection<Long> encounterDiagnosisIds) {
        if (encounterDiagnosisIds == null || encounterDiagnosisIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> uniqueDiagnosisIds = new LinkedHashSet<>();
        // Loop：去重有效就诊诊断主键
        for (Long diagnosisId : encounterDiagnosisIds) {
            if (diagnosisId != null) {
                uniqueDiagnosisIds.add(diagnosisId);
            }
        }
        if (uniqueDiagnosisIds.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<MedicationRequest> specification = (root, query, cb) -> {
            List<Predicate> orPredicates = new ArrayList<>();
            // Loop：任一诊断 id 命中 ids 串即纳入（精确 token，避免 LIKE 误匹配）
            for (Long diagnosisId : uniqueDiagnosisIds) {
                orPredicates.add(EncounterDiagnosisIdsPredicates.containsEncounterDiagnosisId(
                    cb, root.get(FieldNameConstants.ENCOUNTER_DIAGNOSIS_IDS), diagnosisId));
            }
            return cb.and(
                cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
                cb.or(orPredicates.toArray(new Predicate[0])));
        };
        List<MedicationRequest> rows = this.medicationRequestRepository.findAll(specification);
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        // Loop：收集药品申请主键
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                result.add(row.getId());
            }
        }
        return new ArrayList<>(result);
    }
}
