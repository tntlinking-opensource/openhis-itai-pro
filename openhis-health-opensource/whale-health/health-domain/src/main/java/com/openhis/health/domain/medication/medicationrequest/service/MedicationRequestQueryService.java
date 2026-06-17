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

package com.openhis.health.domain.medication.medicationrequest.service;

import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestHistoryQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.repository.MedicationRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 药品医嘱/申请读侧查询服务。
 *
 * <p>职责：按就诊与患者维度查询申请列表与历史，并提供删除、签退等动作前的状态门控；只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicationRequestQueryService {

    private final MedicationRequestRepository medicationRequestRepository;


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
        MedicationRequest request = this.mustGet(medReqId);
        MedicationRequestFifoSignContext ctx = new MedicationRequestFifoSignContext();
        ctx.setMedReqId(request.getId());
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
            if (StringUtils.isBlank(row.getContentJson())) {
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
     * 按就诊查询中药饮片处方列表（按方聚合：已签发按 {@code prescription_no}，草稿按 {@code content_json.prescriptionGroupKey}）。
     *
     * @param encounterId          就诊标识
     * @param generateSourceEnum   生成来源码（医生站通常为 {@link GenerateSource#DOCTOR_PRESCRIPTION}）
     * @return 按方聚合的中医处方读模型；无数据时返回空列表
     */
    public List<TcmHerbalPrescriptionGroupQueryModel> listTcmHerbalPrescriptionsByEncounter(
        Long encounterId, String generateSourceEnum) {
        if (encounterId == null || generateSourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "查询就诊中药处方时 encounterId 与 generateSourceEnum 不能为空");
        }
        List<MedicationRequest> requests = medicationRequestRepository.findByEncounterIdAndGenerateSourceEnum(
            encounterId, generateSourceEnum);
        List<MedicationRequestListQueryModel> tcmLines = new ArrayList<>();
        // Pre：医生开立来源下再筛 tcm_flag，排除软删行。
        for (MedicationRequest request : requests) {
            if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            if (!Boolean.TRUE.equals(request.getTcmFlag())) {
                continue;
            }
            tcmLines.add(this.toListModel(request));
        }
        // Asm：按 prescription_no 或 content_json.prescriptionGroupKey 聚合为方。
        return this.groupTcmHerbalPrescriptionLines(tcmLines);
    }

    /**
     * 查询患者历史中药处方（排除当前就诊，按处方号聚合）。
     *
     * @param patientId          患者标识
     * @param currentEncounterId 当前就诊标识（排除）
     * @return 历史方摘要列表；无数据时返回空列表
     */
    public List<TcmHerbalPrescriptionHistoryGroupQueryModel> listTcmHerbalPrescriptionHistoryByPatient(
        Long patientId, Long currentEncounterId) {
        if (patientId == null || currentEncounterId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "查询患者历史中药处方时 patientId 与 currentEncounterId 不能为空");
        }
        List<MedicationRequest> requests = medicationRequestRepository.findByPatientIdAndEncounterIdNot(
            patientId, currentEncounterId);
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
            if (StringUtils.isBlank(request.getPrescriptionNo())) {
                continue;
            }
            String groupKey = request.getEncounterId() + "|" + request.getPrescriptionNo().trim();
            TcmHerbalPrescriptionHistoryGroupQueryModel group = grouped.get(groupKey);
            if (group == null) {
                group = new TcmHerbalPrescriptionHistoryGroupQueryModel();
                group.setPrescriptionNo(request.getPrescriptionNo().trim());
                group.setEncounterId(request.getEncounterId());
                group.setReqAuthoredTime(request.getReqAuthoredTime());
                group.setLineCount(0);
                grouped.put(groupKey, group);
            }
            if (request.getMedicationId() != null) {
                group.getMedicationIds().add(request.getMedicationId());
            }
            group.setLineCount(group.getLineCount() + 1);
            if (request.getReqAuthoredTime() != null) {
                if (group.getReqAuthoredTime() == null || request.getReqAuthoredTime().isBefore(group.getReqAuthoredTime())) {
                    group.setReqAuthoredTime(request.getReqAuthoredTime());
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

    private List<String> normalizeStatusEnumIn(Collection<String> statusEnumsIn) {
        List<String> statuses = new ArrayList<>();
        if (statusEnumsIn == null) {
            return statuses;
        }
        for (String raw : statusEnumsIn) {
            if (StringUtils.isNotBlank(raw)) {
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
        model.setEncounterId(request.getEncounterId());
        model.setPatientId(request.getPatientId());
        model.setMedicationId(request.getMedicationId());
        model.setGenerateSourceEnum(request.getGenerateSourceEnum());
        model.setRecorderId(request.getRecorderId());
        model.setRequesterId(request.getRequesterId());
        model.setReqAuthoredTime(request.getReqAuthoredTime());
        model.setSortNumber(request.getSortNumber());
        model.setQuantity(request.getQuantity());
        model.setUnitCode(request.getUnitCode());
        model.setMethodCode(request.getMethodCode());
        model.setRateCode(request.getRateCode());
        model.setDose(request.getDose());
        model.setDoseUnitCode(request.getDoseUnitCode());
        model.setLotNumber(request.getLotNumber());
        model.setDispensePerDuration(request.getDispensePerDuration());
        model.setOrgId(request.getOrgId());
        model.setPerformLocation(request.getPerformLocation());
        model.setConditionDefinitionId(request.getConditionDefinitionId());
        model.setEncounterDiagnosisId(request.getEncounterDiagnosisId());
        model.setRemark(request.getRemark());
        model.setContentJson(request.getContentJson());
        model.setRxTypeCode(request.getRxTypeCode());
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
        if (StringUtils.isBlank(statusEnum)) {
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
            if (StringUtils.isNotBlank(therapyEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.THERAPY_ENUM), therapyEnum));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        return medicationRequestRepository.findAll(specification, sort);
    }

    private List<MedicationRequestListQueryModel> mapListModels(List<MedicationRequest> requests) {
        List<MedicationRequestListQueryModel> result = new ArrayList<>();
        for (MedicationRequest request : requests) {
            result.add(this.toListModel(request));
        }
        return result;
    }

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
                group.setStatusEnum(line.getStatusEnum());
                group.setReqAuthoredTime(line.getReqAuthoredTime());
                grouped.put(groupKey, group);
            }
            group.getLines().add(line);
            group.setStatusEnum(this.mergeTcmPrescriptionStatusEnum(group.getStatusEnum(), line.getStatusEnum()));
            if (line.getReqAuthoredTime() != null) {
                if (group.getReqAuthoredTime() == null || line.getReqAuthoredTime().isBefore(group.getReqAuthoredTime())) {
                    group.setReqAuthoredTime(line.getReqAuthoredTime());
                }
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 解析单行所属处方分组键：已签发优先 prescription_no，草稿用 content_json 中的 prescriptionGroupKey。
     */
    private String resolveTcmPrescriptionGroupKey(MedicationRequestListQueryModel line) {
        if (StringUtils.isNotBlank(line.getPrescriptionNo())) {
            return "RX:" + line.getPrescriptionNo().trim();
        }
        String prescriptionGroupKey = this.readTcmContentJsonTextField(line.getContentJson(), "prescriptionGroupKey");
        if (StringUtils.isNotBlank(prescriptionGroupKey)) {
            return "KEY:" + prescriptionGroupKey.trim();
        }
        return "ROW:" + line.getId();
    }

    /**
     * 合并方内多行状态：ACTIVE 优先于 DRAFT，COMPLETED 优先于 ACTIVE。
     */
    private String mergeTcmPrescriptionStatusEnum(String current, String incoming) {
        if (StringUtils.isBlank(current)) {
            return incoming;
        }
        if (StringUtils.isBlank(incoming)) {
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
        if (StringUtils.isBlank(contentJson) || StringUtils.isBlank(fieldName)) {
            return null;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(contentJson);
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                return null;
            }
            String text = node.asText(null);
            if (StringUtils.isBlank(text)) {
                return null;
            }
            return text.trim();
        } catch (Exception ignored) {
            // 读侧解析失败时返回 null，由 resolveTcmPrescriptionGroupKey 按处方号或行 id 兜底。
            return null;
        }
    }
}
