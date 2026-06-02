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
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.repository.MedicationRequestRepository;
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
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;


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
        if (request.getMedicationId() != null) {
            ctx.setCatalogCategoryCode(
                this.medicationDefinitionQueryService.resolveCategoryCodeByMedicationDefinitionId(request.getMedicationId()));
        }
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
        model.setPerformLocation(request.getPerformLocation());
        model.setConditionDefinitionId(request.getConditionDefinitionId());
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
     * 查询患者历史药品申请（排除当前就诊）。
     *
     * @param patientId 患者标识
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
}
