/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsPredicates;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequestDetail;
import com.openhis.health.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.NursingLevelOrderLineQueryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.OffsetVerificationModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestHistoryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestDetailRepository;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 服务申请读侧查询服务。
 *
 * <p>职责：按就诊、患者、诊疗项目等维度查询检查/治疗等服务申请，并提供冲销余额、重复转科出院门控等只读编排；不写库。</p>
 * <p>聚合边界：仅本聚合 {@link ServiceRequestRepository} / {@link ServiceRequestDetailRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestQueryService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceRequestDetailRepository serviceRequestDetailRepository;

    /**
     * 按主键尝试加载服务申请；不存在返回 null（与强制加载区分，供展示型接口）。
     *
     * @param id 服务申请主键
     * @return 读模型或 null
     */
    public ServiceRequestQueryModel loadServiceRequestById(Long id) {
        ServiceRequest request = serviceRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return null;
        }
        return this.toQueryModel(request);
    }

    /**
     * 按床位自动计费配置规则主键解析关联的长期床位滚费医嘱（AS-IP-NUR-013 BR-IP-ARG-019）。
     *
     * @param ruleId 配置表主键 {@code cus_charge_item_auto_gen.id}
     * @return 诊疗申请主键；无命中时 empty
     */
    public Optional<Long> findAutoRollBedServiceRequestIdByRuleId(Long ruleId) {
        if (ruleId == null) {
            return Optional.empty();
        }
        Optional<ServiceRequest> row = serviceRequestRepository.findFirstByBasedOnTableAndBasedOnIdAndGenerateSourceEnumAndIsDeletedFalse(
            TableNameConstants.CUS_CHARGE_ITEM_AUTO_GEN,
            ruleId,
            GenerateSource.AUTO_ROLL_FEES.getCode());
        if (row.isEmpty() || row.get().getId() == null) {
            return Optional.empty();
        }
        return Optional.of(row.get().getId());
    }

    /**
     * 解析服务申请关联的诊疗项目 id（展示预热，不存在时返回 null）。
     *
     * @param serviceRequestId 服务申请主键
     * @return 诊疗项目 activity_id 或 null
     */
    public Long resolveActivityIdByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return null;
        }
        ServiceRequestListModel row = this.mapListModelsByIds(List.of(serviceRequestId)).get(serviceRequestId);
        if (row == null) {
            return null;
        }
        return row.getActivityId();
    }

    /**
     * 按主键批量加载服务申请列表行（护士站已划价列表等产品名/数量回源）。
     *
     * @param serviceRequestIds 服务申请主键集合
     * @return 主键 → 列表行
     */
    public Map<Long, ServiceRequestListModel> mapListModelsByIds(Collection<Long> serviceRequestIds) {
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : serviceRequestIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ServiceRequest> rows = serviceRequestRepository.findAllById(distinctIds);
        Map<Long, ServiceRequestListModel> map = new HashMap<>();
        for (ServiceRequest request : rows) {
            if (request == null || request.getId() == null || Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            map.put(request.getId(), this.toListModel(request));
        }
        return map;
    }

    /**
     * 护士工作量：按就诊集合批量解析 {@code category_enum}（DM-WKF-004 §6.2.26；AS-RPT-002）。
     *
     * @param encounterIds 就诊主键集合
     * @return serviceRequestId → categoryEnum；无命中时返回空 Map
     */
    public Map<Long, String> mapCategoryEnumByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctEncounterIds = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                distinctEncounterIds.add(encounterId);
            }
        }
        if (distinctEncounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // Pre：按就诊集合批量加载 SR，构建 serviceRequestId → category_enum 索引
        List<ServiceRequest> rows =
            serviceRequestRepository.findByEncounterIdInAndIsDeletedFalse(distinctEncounterIds);
        Map<Long, String> categoryByServiceRequestId = new HashMap<>();
        for (ServiceRequest request : rows) {
            if (request == null || request.getId() == null) {
                continue;
            }
            categoryByServiceRequestId.put(request.getId(), request.getCategoryEnum());
        }
        return categoryByServiceRequestId;
    }

    /**
     * 按主键强制加载服务申请。
     *
     * @param id 服务申请主键
     * @return 读模型
     * @throws BusinessException 申请不存在
     */
    public ServiceRequestQueryModel mustGetServiceRequest(Long id) {
        ServiceRequest request = serviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在"));
        return this.toQueryModel(request);
    }

    /**
     * 按就诊列出全部服务申请摘要。
     *
     * @param encounterId 就诊标识
     * @return 列表
     * @throws BusinessException 就诊标识为空
     */
    public List<ServiceRequestListModel> listServiceRequestsByEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "按就诊查询服务申请时 encounterId 不能为空");
        }
        List<ServiceRequest> requests = serviceRequestRepository.findByEncounterId(encounterId);
        List<ServiceRequestListModel> models = new ArrayList<>();
        for (ServiceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 计时诊疗累计用量（吸氧/心电等）：按就诊 + 活动定义汇总 {@code quantity}（小时数）。
     *
     * <p>{@code occurrenceDate} 非空时仅统计该自然日（优先 {@code occurrence_start_time}，否则 {@code authored_time}）。</p>
     *
     * @param encounterId 就诊标识
     * @param activityId  诊疗活动定义主键
     * @param occurrenceDate 发生自然日过滤，可空表示在院全量累计
     * @return 已累计小时数（quantity 之和向下取整）
     */
    public long sumQuantityHoursByEncounterAndActivity(
        Long encounterId,
        Long activityId,
        LocalDate occurrenceDate) {
        if (encounterId == null || activityId == null) {
            return 0L;
        }
        List<ServiceRequest> requests = serviceRequestRepository.findByEncounterId(encounterId);
        BigDecimal total = BigDecimal.ZERO;
        for (ServiceRequest request : requests) {
            if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            if (!activityId.equals(request.getActivityId())) {
                continue;
            }
            if (occurrenceDate != null && !this.matchesTimedServiceOccurrenceDate(request, occurrenceDate)) {
                continue;
            }
            if (request.getQuantity() != null) {
                total = total.add(request.getQuantity());
            }
        }
        return total.longValue();
    }

    /**
     * 医生站历史医嘱：在患者范围内收集存在医生开立诊疗申请的就诊主键（排除当前就诊）。
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
        Specification<ServiceRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.ENCOUNTER_ID), excludeEncounterId));
            predicates.add(cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), doctorSource));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ServiceRequest> rows = serviceRequestRepository.findAll(specification);
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // Loop：从医生开立诊疗行提取就诊 id，LinkedHashSet 保序去重。
        for (ServiceRequest row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        return new ArrayList<>(encounterIds);
    }

    /**
     * 查询患者历史服务申请并排除当前就诊，用于既往史侧栏。
     *
     * @param patientId 患者标识
     * @param excludeEncounterId 当前就诊标识（排除）
     * @return 历史列表
     * @throws BusinessException 入参不完整
     */
    public List<ServiceRequestHistoryModel> listServiceRequestHistoryByPatient(Long patientId, Long excludeEncounterId) {
        if (patientId == null || excludeEncounterId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "查询患者历史服务申请时 patientId/excludeEncounterId 不能为空");
        }
        List<ServiceRequest> requests = serviceRequestRepository.findByPatientIdAndEncounterIdNot(patientId, excludeEncounterId);
        List<ServiceRequestHistoryModel> models = new ArrayList<>();
        for (ServiceRequest request : requests) {
            ServiceRequestHistoryModel model = new ServiceRequestHistoryModel();
            model.setId(request.getId());
            model.setPatientId(request.getPatientId());
            model.setEncounterId(request.getEncounterId());
            models.add(model);
        }
        return models;
    }

    /**
     * 门控：仅草稿态（持久化值等于 {@link RequestStatus#DRAFT} 的 {@link RequestStatus#getCode()}）。
     *
     * @param id 服务申请主键
     * @return 可删除时为 true
     * @throws BusinessException 记录不存在
     */
    public Boolean validateServiceRequestDeletable(Long id) {
        ServiceRequest request = serviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在"));
        return request.getStatusEnum() != null && request.getStatusEnum().equals(RequestStatus.DRAFT.getCode());
    }

    /**
     * 门控：仅已发送态（持久化值等于 {@link RequestStatus#ACTIVE} 的 {@link RequestStatus#getCode()}）允许撤回类操作。
     *
     * @param id 服务申请主键
     * @return 可撤回时为 true
     * @throws BusinessException 记录不存在
     */
    public Boolean validateServiceRequestWithdrawable(Long id) {
        ServiceRequest request = serviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在"));
        return request.getStatusEnum() != null && request.getStatusEnum().equals(RequestStatus.ACTIVE.getCode());
    }

    /**
     * 冲销校验：汇总 refund 指向本单且数量为负的各行绝对值，与源单数量及本单拟冲数量求剩余可冲额度。
     *
     * @param sourceId 原服务申请主键
     * @param preOffset 本单拟冲数量，null 视为 0
     * @return 剩余数量等结果
     */
    public OffsetVerificationModel assertOffsetVerification(Long sourceId, BigDecimal preOffset) {
        if (sourceId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "冲销校验 sourceId 不能为空");
        }
        if (preOffset == null) {
            preOffset = BigDecimal.ZERO;
        }
        ServiceRequest source = serviceRequestRepository.findById(sourceId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "原服务申请不存在"));
        if (source.getQuantity() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "原服务申请 quantity 不能为空");
        }

        List<ServiceRequest> negativeRows = serviceRequestRepository.findByRefundServiceIdAndQuantityLessThan(
            sourceId, BigDecimal.ZERO);
        BigDecimal offset = BigDecimal.ZERO;
        for (ServiceRequest row : negativeRows) {
            if (row.getQuantity() == null) {
                continue;
            }
            offset = offset.add(row.getQuantity().abs());
        }

        BigDecimal remaining = source.getQuantity().subtract(offset).subtract(preOffset);
        OffsetVerificationModel model = new OffsetVerificationModel();
        model.setSourceId(sourceId);
        model.setRemaining(remaining);
        return model;
    }

    /**
     * 按就诊与治疗时长类型过滤服务申请。
     *
     * @param encounterId 就诊标识
     * @param therapyEnum 治疗时长类型编码
     * @return 列表
     * @throws BusinessException 入参不完整
     */
    public List<ServiceRequestListModel> listServiceRequestsByTherapyType(Long encounterId, String therapyEnum) {
        if (encounterId == null || therapyEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "按治疗时长类型查询时 encounterId/therapyEnum 不能为空");
        }
        List<ServiceRequest> requests = serviceRequestRepository.findByEncounterIdAndTherapyEnum(encounterId, therapyEnum);
        List<ServiceRequestListModel> models = new ArrayList<>();
        for (ServiceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 按就诊与生成来源枚举过滤服务申请。
     *
     * @param encounterId 就诊标识
     * @param sourceEnum 生成来源编码
     * @return 列表
     * @throws BusinessException 入参不完整
     */
    public List<ServiceRequestListModel> listServiceRequestsByGenerateSource(Long encounterId, String sourceEnum) {
        if (encounterId == null || sourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "按生成来源查询时 encounterId/sourceEnum 不能为空");
        }
        List<ServiceRequest> requests = serviceRequestRepository.findByEncounterIdAndGenerateSourceEnum(encounterId, sourceEnum);
        List<ServiceRequestListModel> models = new ArrayList<>();
        for (ServiceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 按就诊、申请单号与生成来源查询服务申请列表行（申请单编辑/列表读侧）。
     *
     * @param encounterId        就诊主键
     * @param prescriptionNo     申请单号（完整匹配）
     * @param generateSourceEnum 生成来源 code
     * @return 列表行；无匹配时返回空列表
     */
    public List<ServiceRequestListModel> listByEncounterAndPrescriptionNo(
        Long encounterId, String prescriptionNo, String generateSourceEnum) {
        if (encounterId == null || StringUtils.isEmpty(prescriptionNo) || StringUtils.isEmpty(generateSourceEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "按申请单号查询时 encounterId/prescriptionNo/generateSourceEnum 不能为空");
        }
        List<ServiceRequest> requests = serviceRequestRepository.findByEncounterIdAndPrescriptionNoAndGenerateSourceEnum(
            encounterId, prescriptionNo.trim(), generateSourceEnum.trim());
        return this.mapListModels(requests);
    }

    /**
     * 按诊疗项目定义主键查询已开立的服务申请。
     *
     * @param activityId 诊疗定义主键
     * @return 列表
     * @throws BusinessException activityId 为空
     */
    public List<ServiceRequestListModel> getServiceRequestByActivityId(Long activityId) {
        if (activityId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "按诊疗定义查询时 activityId 不能为空");
        }
        List<ServiceRequest> requests = serviceRequestRepository.findByActivityId(activityId);
        List<ServiceRequestListModel> models = new ArrayList<>();
        for (ServiceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 判断指定诊疗定义是否已被有效服务申请引用（目录编辑门控）。
     *
     * @param activityId 诊疗定义主键
     * @return 存在引用时为 true
     */
    public boolean existsByActivityId(Long activityId) {
        if (activityId == null) {
            return false;
        }
        return serviceRequestRepository.existsByActivityIdAndIsDeletedFalse(activityId);
    }

    /**
     * 就诊维度下是否已存在未完结的转科或出院类申请（类别 32/34 且状态为草稿或进行中）。
     *
     * @param encounterId 就诊标识
     * @return 存在则为 true
     * @throws BusinessException 就诊标识为空
     */
    public Boolean checkDuplicateTransferOrDischargeRequest(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "校验转科/出院重复时 encounterId 不能为空");
        }
        List<String> categories = new ArrayList<>();
        categories.add("32");
        categories.add("34");
        List<ServiceRequest> requests = serviceRequestRepository.findByEncounterIdAndCategoryEnumIn(encounterId, categories);
        for (ServiceRequest request : requests) {
            if (request.getStatusEnum() == null) {
                continue;
            }
            // 草稿/已发送视为仍占用流程，禁止重复发起同类申请
            if (request.getStatusEnum().equals(RequestStatus.DRAFT.getCode())
                || request.getStatusEnum().equals(RequestStatus.ACTIVE.getCode())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /**
     * 就诊是否已有已校对的异类转科/出院诊疗（校对联动硬门禁，GAP-ATD-006 阶段 C）。
     *
     * @param encounterId        就诊主键
     * @param categoryEnum       当前校对类别（{@code 32} 转科 / {@code 34} 出院）
     * @param excludingRequestId 排除的申请主键（当前正在校对）
     * @return 存在异类已校对申请时为 true
     * @throws BusinessException 就诊主键为空
     */
    public boolean hasVerifiedOppositeTransferOrDischargeRequest(
        Long encounterId,
        String categoryEnum,
        Long excludingRequestId) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "校验转科/出院异类已校对时 encounterId 不能为空");
        }
        String oppositeCategory;
        if ("32".equals(categoryEnum)) {
            oppositeCategory = "34";
        } else if ("34".equals(categoryEnum)) {
            oppositeCategory = "32";
        } else {
            return false;
        }
        List<String> categories = new ArrayList<>();
        categories.add(oppositeCategory);
        List<ServiceRequest> requests =
            serviceRequestRepository.findByEncounterIdAndCategoryEnumIn(encounterId, categories);
        for (ServiceRequest request : requests) {
            if (request == null || request.getId() == null) {
                continue;
            }
            if (excludingRequestId != null && excludingRequestId.equals(request.getId())) {
                continue;
            }
            if (RequestStatus.VERIFIED.getCode().equals(request.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 就诊是否仍有已校对的转科/出院诊疗（逆向编排 Pre 门禁，GAP-ATD-008）。
     *
     * @param encounterId        就诊主键
     * @param excludingRequestId 排除的申请主键（当前正在撤销）
     * @return 存在其它已校对转科/出院申请时为 true
     */
    public boolean hasVerifiedTransferOrDischargeRequest(Long encounterId, Long excludingRequestId) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "校验转科/出院已校对时 encounterId 不能为空");
        }
        List<String> categories = new ArrayList<>();
        categories.add("32");
        categories.add("34");
        List<ServiceRequest> requests =
            serviceRequestRepository.findByEncounterIdAndCategoryEnumIn(encounterId, categories);
        for (ServiceRequest request : requests) {
            if (request == null || request.getId() == null) {
                continue;
            }
            if (excludingRequestId != null && excludingRequestId.equals(request.getId())) {
                continue;
            }
            if (RequestStatus.VERIFIED.getCode().equals(request.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按就诊主键集合与请求状态批量查询诊疗申请（只读；供应用层批量计数等编排）。
     *
     * @param encounterIds 就诊主键集合
     * @param statusEnum   请求状态 {@link RequestStatus#getCode()}
     * @return 诊疗申请列表；入参为空时返回空列表
     * @throws BusinessException statusEnum 为空
     */
    public List<ServiceRequest> listByEncounterIdsAndStatusEnum(
        Collection<Long> encounterIds,
        String statusEnum) {
        Collection<Long> idSet = this.distinctNonNullEncounterIds(encounterIds);
        if (idSet.isEmpty()) {
            return Collections.emptyList();
        }
        if (StringUtils.isEmpty(statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "按就诊批量查询时 statusEnum 不能为空");
        }
        return serviceRequestRepository.findByEncounterIdInAndStatusEnum(idSet, statusEnum.trim());
    }

    /**
     * 住院医生站诊疗医嘱列表（替代 OrderInfo RM Q-ORD-007 诊疗子集）。
     */
    public List<ServiceRequestListModel> listInpatientDoctorStationOrders(Long encounterId, String therapyEnum) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "住院医生站诊疗医嘱列表查询时 encounterId 不能为空");
        }
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(encounterId, List.of(encounterId), null, therapyEnum));
    }

    /**
     * 住院护士站校对诊疗医嘱列表（替代 OrderInfo RM Q-IP-AUD-001 诊疗子集）。
     */
    public List<ServiceRequestListModel> listInpatientNurseStationAuditOrders(
        List<Long> encounterIds, List<String> statusEnumIn, String therapyEnum) {
        this.validateEncounterIdsAndStatusIn(encounterIds, statusEnumIn, "住院护士站诊疗校对列表");
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(null, encounterIds, statusEnumIn, therapyEnum));
    }

    /**
     * 住院护士站执行诊疗医嘱列表（替代 OrderInfo RM Q-IP-EXE-001 诊疗子集）。
     *
     * <p>状态含 {@link RequestStatus#VERIFIED}、{@link RequestStatus#STOPPED}（停嘱后仍在执行列表展示）。</p>
     */
    public List<ServiceRequestListModel> listInpatientNurseStationExecuteOrders(
        List<Long> encounterIds, String therapyEnum) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "住院护士站诊疗执行列表查询时 encounterIds 不能为空");
        }
        List<String> executeStatuses = List.of(
            RequestStatus.VERIFIED.getCode(),
            RequestStatus.STOPPED.getCode());
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(null, encounterIds, executeStatuses, therapyEnum));
    }

    /**
     * 住院护士站补费父诊疗医嘱列表（替代 OrderInfo RM Q-IP-PRC-002 诊疗子集）。
     */
    public List<ServiceRequestListModel> listInpatientNurseStationSupplementOrders(
        List<Long> encounterIds, String therapyEnum) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "住院护士站补费父诊疗列表查询时 encounterIds 不能为空");
        }
        List<String> parentStatuses = List.of(RequestStatus.VERIFIED.getCode(), RequestStatus.STOPPED.getCode());
        return this.mapListModels(this.findInpatientDoctorPrescriptionOrders(null, encounterIds, parentStatuses, therapyEnum));
    }

    /**
     * 住院护士站已划价诊疗医嘱列表（替代 OrderInfo RM Q-IP-PRC-001 诊疗子集）。
     */
    public List<ServiceRequestListModel> listInpatientNurseStationPricedOrders(
        List<Long> encounterIds,
        LocalDateTime pricedTimeFrom,
        LocalDateTime pricedTimeTo,
        Long parentBasedOnId) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "住院护士站已划价诊疗列表查询时 encounterIds 不能为空");
        }
        return this.mapListModels(this.findInpatientNursePricingOrders(encounterIds, pricedTimeFrom, pricedTimeTo, parentBasedOnId));
    }

    /**
     * 按主键解析医嘱业务单号（adapter 读路径）。
     */
    public String resolveBusNoByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return null;
        }
        ServiceRequest request = serviceRequestRepository.findById(serviceRequestId).orElse(null);
        if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
            return null;
        }
        return request.getBusNo();
    }

    /**
     * 按原服务申请主键批量查询关联退诊疗/负向申请（DM-WKF-004 §6.2；AS-OP-FIN-003 IF-REF-04 扩展）。
     *
     * @param refundServiceIds 原服务申请主键集合（{@code refund_service_id} 指向方）
     * @return 命中关联字段的申请行；入参为空时返回空列表
     */
    public List<ServiceRequest> listByRefundServiceIdIn(Collection<Long> refundServiceIds) {
        if (refundServiceIds == null || refundServiceIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : refundServiceIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return this.serviceRequestRepository.findByRefundServiceIdIn(ids);
    }

    /**
     * 按主键解析医嘱所属就诊（adapter 读路径）。
     */
    public Long resolveEncounterIdByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return null;
        }
        ServiceRequest request = serviceRequestRepository.findById(serviceRequestId).orElse(null);
        if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
            return null;
        }
        return request.getEncounterId();
    }

    /**
     * 解析 billDate 当日最后有效校对护理级别（AS-IP-NUR-013 BR-IP-ARG-009；DM-WKF-004 §6.2.25）。
     *
     * <p>口径：医生开立 + category {@code 26}；仅 {@code VERIFIED}（已校对未停嘱）参与滚费，与自动计费配置
     * {@code ACTIVE} 同构——{@code STOPPED} 一经停嘱即退出滚费池；须另开新护理级别或办出院。
     * 校对日晚于 billDate 的行在 billDate 尚未生效；同日换级取 {@code check_time} 最大的 {@code VERIFIED}。</p>
     *
     * @param encounterId 就诊主键
     * @param billDate    滚费业务日（前一日自然日）
     * @return 有效护理级别；无匹配时 empty
     * @throws BusinessException {@code encounterId} 或 {@code billDate} 为空
     */
    public Optional<InpatientAutoRollNursingLevelQueryModel> findLastEffectiveNursingLevelForAutoRoll(
        Long encounterId,
        LocalDate billDate) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "解析滚费护理级别时 encounterId 不能为空");
        }
        if (billDate == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "解析滚费护理级别时 billDate 不能为空");
        }
        // Pre：仅 VERIFIED——STOPPED 与配置 INACTIVE 同义，永不参与滚费
        Specification<ServiceRequest> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), encounterId),
            cb.equal(root.get(FieldNameConstants.CATEGORY_ENUM), ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING),
            cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), GenerateSource.DOCTOR_PRESCRIPTION.getCode()),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), RequestStatus.VERIFIED.getCode()));
        List<ServiceRequest> rows = serviceRequestRepository.findAll(specification);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // Loop：billDate 已生效的 VERIFIED 中取 check_time 最大者（换级 stop-old-start-new）
        ServiceRequest best = null;
        for (ServiceRequest row : rows) {
            if (row == null || row.getCheckTime() == null) {
                continue;
            }
            LocalDate verifiedDate = row.getCheckTime().toLocalDate();
            // 校对日晚于 billDate：该级别在 billDate 尚未校对生效
            if (verifiedDate.isAfter(billDate)) {
                continue;
            }
            if (best == null) {
                best = row;
                continue;
            }
            if (row.getCheckTime().isAfter(best.getCheckTime())) {
                best = row;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(this.toNursingLevelQueryModel(best));
    }

    /**
     * 同就诊是否存在并发有效护理级别医嘱（医生开立；排除指定主键）。
     *
     * <p>有效态：{@code DRAFT}/{@code ACTIVE}/{@code VERIFIED}；已停嘱/已取消不计入（BR-SRQ-020；AS-IP-DOR-003 BR-IP-ORD-026）。</p>
     *
     * @param encounterId            就诊主键
     * @param excludeServiceRequestId 排除的护理级别医嘱主键（修改/签发自身时传入）；新建时传 null
     * @return 存在其它有效护理级别医嘱时为 true
     */
    public boolean existsConcurrentNursingLevelOrder(Long encounterId, Long excludeServiceRequestId) {
        if (encounterId == null) {
            return false;
        }
        List<ServiceRequest> rows = serviceRequestRepository.findByEncounterIdAndCategoryEnumIn(
            encounterId, List.of(ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING));
        for (ServiceRequest row : rows) {
            if (row == null) {
                continue;
            }
            if (excludeServiceRequestId != null && excludeServiceRequestId.equals(row.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum())) {
                continue;
            }
            if (RequestStatus.STOPPED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (RequestStatus.CANCELLED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 病案首页护理天数：列出就诊下医生开立的护理级别医嘱行（已校对或已停嘱，含时间窗）。
     *
     * <p>仅本聚合 {@link ServiceRequestRepository}；级别名称由调用方经 ActivityDefinition 解析。</p>
     *
     * @param encounterId 就诊主键
     * @return 护理级别医嘱行；无数据时 empty
     */
    public List<NursingLevelOrderLineQueryModel> listNursingLevelOrderLinesForCaseRecord(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        List<ServiceRequest> rows = serviceRequestRepository.findByEncounterIdAndCategoryEnumIn(
            encounterId, List.of(ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING));
        List<NursingLevelOrderLineQueryModel> result = new ArrayList<>();
        for (ServiceRequest row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            // 仅医生开立护理级别
            if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum())) {
                continue;
            }
            String status = row.getStatusEnum();
            // 未校对草稿不计入天数；已校对或已停嘱参与累计
            boolean verified = RequestStatus.VERIFIED.getCode().equals(status);
            boolean stopped = RequestStatus.STOPPED.getCode().equals(status);
            if (!verified && !stopped) {
                continue;
            }
            // 无校对时间无法定生效日起点
            if (row.getCheckTime() == null) {
                continue;
            }
            NursingLevelOrderLineQueryModel line = new NursingLevelOrderLineQueryModel();
            line.setServiceRequestId(row.getId());
            line.setActivityId(row.getActivityId());
            line.setStatusEnum(status);
            line.setCheckTime(row.getCheckTime());
            line.setStopTime(row.getStopTime());
            result.add(line);
        }
        return result;
    }

    /**
     * 批量解析患者队列展示用当前护理级别（AS-IP-NUR-001 IF-IP-NUR-02 / AS-IP-DOR-001 IF-IP-DOR-01）。
     *
     * <p>口径：仅展示仍为 {@code VERIFIED} 的校对护理级别（与滚费 {@link #findLastEffectiveNursingLevelForAutoRoll} 同构；
     * {@code STOPPED} 不展示为当前级别）；{@code referenceDate} 当日最后校对生效者；未校对 {@code DRAFT}/{@code ACTIVE} 不参与。</p>
     *
     * @param encounterIds  就诊主键集合
     * @param referenceDate 展示参考自然日；null 时按调用方约定（App 层通常传当日）
     * @return encounterId → 护理级别读模型；无匹配就诊不出现在 Map 中
     */
    public Map<Long, InpatientAutoRollNursingLevelQueryModel> mapNursingLevelForPatientQueueByEncounterIds(
        Collection<Long> encounterIds,
        LocalDate referenceDate) {
        Map<Long, InpatientAutoRollNursingLevelQueryModel> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        List<Long> encounterIdList = new ArrayList<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                encounterIdList.add(encounterId);
            }
        }
        if (encounterIdList.isEmpty()) {
            return result;
        }
        LocalDate billDate = referenceDate;
        if (billDate == null) {
            billDate = LocalDate.now();
        }
        // Pre：队列当前级别仅 VERIFIED；STOPPED 已退出护理滚费池，不展示为当前级别
        Specification<ServiceRequest> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIdList),
            cb.equal(root.get(FieldNameConstants.CATEGORY_ENUM), ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING),
            cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), GenerateSource.DOCTOR_PRESCRIPTION.getCode()),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), RequestStatus.VERIFIED.getCode()));
        List<ServiceRequest> rows = serviceRequestRepository.findAll(specification);
        if (rows.isEmpty()) {
            return result;
        }
        Map<Long, List<ServiceRequest>> rowsByEncounterId = new HashMap<>();
        for (ServiceRequest row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            List<ServiceRequest> bucket = rowsByEncounterId.get(row.getEncounterId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByEncounterId.put(row.getEncounterId(), bucket);
            }
            bucket.add(row);
        }
        for (Map.Entry<Long, List<ServiceRequest>> entry : rowsByEncounterId.entrySet()) {
            InpatientAutoRollNursingLevelQueryModel model =
                this.resolveNursingLevelForPatientQueue(entry.getValue(), billDate);
            if (model != null) {
                result.put(entry.getKey(), model);
            }
        }
        return result;
    }

    /**
     * 按主键加载诊疗申请列表行（供按表路由）。
     */
    public ServiceRequestListModel getListModelById(Long id) {
        if (id == null) {
            return null;
        }
        ServiceRequest request = serviceRequestRepository.findById(id).orElse(null);
        if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
            return null;
        }
        return this.toListModel(request);
    }

    /**
     * 按主键加载未删诊疗申请（Optional 包装，供跨模块反查）。
     *
     * @param id 服务申请主键
     * @return 列表模型；不存在或已删则 empty
     */
    public Optional<ServiceRequestListModel> findActiveListModelById(Long id) {
        ServiceRequestListModel model = this.getListModelById(id);
        if (model == null) {
            return Optional.empty();
        }
        return Optional.of(model);
    }

    /**
     * 按 prescription_no 精确匹配未删服务申请，取 id 最大的一条（MedSync APPLYNO 反查主路径）。
     *
     * @param prescriptionNo 申请单号
     * @return 最新一条；无命中 empty
     */
    public Optional<ServiceRequestListModel> findFirstActiveByPrescriptionNo(String prescriptionNo) {
        if (StringUtils.isEmpty(prescriptionNo)) {
            return Optional.empty();
        }
        List<ServiceRequest> rows =
            serviceRequestRepository.findByPrescriptionNoAndIsDeletedFalse(prescriptionNo.trim());
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        ServiceRequest latest = null;
        for (ServiceRequest row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (latest == null || row.getId() > latest.getId()) {
                latest = row;
            }
        }
        if (latest == null) {
            return Optional.empty();
        }
        return Optional.of(this.toListModel(latest));
    }

    /**
     * 按主键强制加载诊疗申请列表行。
     */
    public ServiceRequestListModel mustGetListModelById(Long id) {
        return this.toListModel(serviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在")));
    }

    /**
     * 住院医生开立医嘱查询：来源为医生处方、可按单就诊/批量就诊、状态与长临过滤。
     *
     * @param singleEncounterId 单就诊过滤；非空时忽略 encounterIds
     * @param encounterIds      批量就诊过滤
     * @param statusEnumIn      请求状态 code 集合；可 null 表示不限
     * @param therapyEnum       长临 code；可空表示不限
     * @return 未逻辑删除的实体列表，按创建时间降序
     */
    private List<ServiceRequest> findInpatientDoctorPrescriptionOrders(
        Long singleEncounterId,
        List<Long> encounterIds,
        List<String> statusEnumIn,
        String therapyEnum) {
        Specification<ServiceRequest> specification = (root, criteriaQuery, cb) -> {
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
        return serviceRequestRepository.findAll(specification, sort);
    }

    /**
     * 住院护士补费/计价医嘱查询：来源为护士计价，按就诊集合与时间窗过滤。
     *
     * @param encounterIds      就诊主键集合
     * @param pricedTimeFrom    计价时间下界；可 null
     * @param pricedTimeTo      计价时间上界；可 null
     * @param parentBasedOnId   父锚点 based_on_id；可 null 表示不限
     * @return 未逻辑删除的实体列表，按创建时间降序
     */
    private List<ServiceRequest> findInpatientNursePricingOrders(
        List<Long> encounterIds,
        LocalDateTime pricedTimeFrom,
        LocalDateTime pricedTimeTo,
        Long parentBasedOnId) {
        Specification<ServiceRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIds));
            predicates.add(cb.equal(
                root.get(FieldNameConstants.GENERATE_SOURCE_ENUM),
                GenerateSource.NURSE_PRICING.getCode()));
            if (pricedTimeFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), pricedTimeFrom));
            }
            if (pricedTimeTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), pricedTimeTo));
            }
            if (parentBasedOnId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.BASED_ON_ID), parentBasedOnId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        return serviceRequestRepository.findAll(specification, sort);
    }

    /**
     * 批量将诊疗申请实体映射为列表读模型。
     *
     * @param requests 实体集合
     * @return 列表模型；空入参时返回空列表
     */
    private List<ServiceRequestListModel> mapListModels(List<ServiceRequest> requests) {
        List<ServiceRequestListModel> models = new ArrayList<>();
        for (ServiceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 批量住院查询入参门禁：就诊集合与状态集合均不可空。
     *
     * @param encounterIds  就诊主键集合
     * @param statusEnumIn  请求状态 code 集合
     * @param sceneLabel    场景标签（拼入异常文案）
     * @throws BusinessException encounterIds 或 statusEnumIn 为空
     */
    private void validateEncounterIdsAndStatusIn(
        List<Long> encounterIds, List<String> statusEnumIn, String sceneLabel) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                sceneLabel + "查询时 encounterIds 不能为空");
        }
        if (statusEnumIn == null || statusEnumIn.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
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
     * 统计基于某业务主表行派生的服务申请条数（basedOnTable 空时按空串计）。
     *
     * @param basedOnTable 来源表名，可为 null
     * @param basedOnId 来源主键
     * @return 条数
     * @throws BusinessException basedOnId 为空
     */
    public Long countServiceRequestByBasedOnId(String basedOnTable, Long basedOnId) {
        if (basedOnId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "统计派生步次时 basedOnId 不能为空");
        }
        if (basedOnTable == null) {
            basedOnTable = "";
        }
        return serviceRequestRepository.countByBasedOnTableAndBasedOnId(basedOnTable, basedOnId);
    }

    /**
     * 按来源锚点查询派生服务申请列表（取消执行绑定诊疗 based_on 反查，DM-WKF-004 §6.2.23）。
     *
     * @param basedOnTable 来源表名
     * @param basedOnId    来源主键
     * @return 服务申请列表
     * @throws BusinessException basedOnId 为空
     */
    public List<ServiceRequest> listServiceRequestsByBasedOnAnchor(String basedOnTable, Long basedOnId) {
        if (basedOnId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "按来源锚点查询时 basedOnId 不能为空");
        }
        if (basedOnTable == null) {
            basedOnTable = "";
        }
        return serviceRequestRepository.findByBasedOnTableAndBasedOnId(basedOnTable, basedOnId);
    }

    /**
     * 按来源表名与多条来源主键批量查询派生服务申请（取消执行批量 based_on 反查）。
     *
     * @param basedOnTable 来源表名
     * @param basedOnIds   来源主键集合
     * @return 服务申请列表
     * @throws BusinessException basedOnIds 为空
     */
    public List<ServiceRequest> listServiceRequestsByBasedOnAnchors(String basedOnTable, Collection<Long> basedOnIds) {
        if (basedOnIds == null || basedOnIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "批量按来源锚点查询时 basedOnIds 不能为空");
        }
        if (basedOnTable == null) {
            basedOnTable = "";
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long basedOnId : basedOnIds) {
            if (basedOnId != null) {
                distinctIds.add(basedOnId);
            }
        }
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return serviceRequestRepository.findByBasedOnTableAndBasedOnIdIn(basedOnTable, distinctIds);
    }

    /**
     * 按服务申请主键批量加载未逻辑删除的明细实体（供 querysupport 展示 enrich）。
     *
     * @param serviceRequestIds 服务申请主键集合
     * @return 明细实体列表；无命中时空列表
     */
    public List<ServiceRequestDetail> findActiveDetailsByServiceRequestIds(Collection<Long> serviceRequestIds) {
        Collection<Long> idSet = this.distinctNonNullServiceRequestIds(serviceRequestIds);
        if (idSet.isEmpty()) {
            return Collections.emptyList();
        }
        return this.serviceRequestDetailRepository.findByServiceReqIdIn(new ArrayList<>(idSet));
    }

    /**
     * 就诊主键去重（批量明细查询入参归一化）。
     *
     * @param serviceRequestIds 原始服务申请主键集合
     * @return 非空去重集合
     */
    private Collection<Long> distinctNonNullServiceRequestIds(Collection<Long> serviceRequestIds) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        if (serviceRequestIds == null) {
            return idSet;
        }
        for (Long serviceRequestId : serviceRequestIds) {
            if (serviceRequestId != null) {
                idSet.add(serviceRequestId);
            }
        }
        return idSet;
    }

    /**
     * 详情读模型映射（状态与类别等头字段）。
     *
     * @param request 实体
     * @return 查询模型
     */
    private ServiceRequestQueryModel toQueryModel(ServiceRequest request) {
        ServiceRequestQueryModel model = new ServiceRequestQueryModel();
        model.setId(request.getId());
        model.setStatusEnum(request.getStatusEnum());
        model.setCategoryEnum(request.getCategoryEnum());
        return model;
    }

    /**
     * 列表行映射。
     *
     * @param request 实体
     * @return 列表模型
     */
    private ServiceRequestListModel toListModel(ServiceRequest request) {
        ServiceRequestListModel model = new ServiceRequestListModel();
        model.setId(request.getId());
        model.setEncounterId(request.getEncounterId());
        model.setTherapyEnum(request.getTherapyEnum());
        model.setPatientId(request.getPatientId());
        model.setStatusEnum(request.getStatusEnum());
        model.setRefundServiceId(request.getRefundServiceId());
        model.setActivityId(request.getActivityId());
        model.setCategoryEnum(request.getCategoryEnum());
        model.setGenerateSourceEnum(request.getGenerateSourceEnum());
        model.setReasonText(request.getReasonText());
        model.setOccurrenceStartTime(request.getOccurrenceStartTime());
        model.setRequesterId(request.getRequesterId());
        model.setPerformerCheckId(request.getPerformerCheckId());
        model.setReqOpenedTime(request.getReqOpenedTime());
        model.setReqAuthoredTime(request.getReqAuthoredTime());
        model.setSortNumber(request.getSortNumber());
        model.setQuantity(request.getQuantity());
        model.setUnitCode(request.getUnitCode());
        model.setExecutionCount(request.getExecutionCount());
        model.setContentJson(request.getContentJson());
        model.setRemark(request.getRemark());
        model.setEntrustContent(request.getEntrustContent());
        model.setLocationId(request.getLocationId());
        model.setOrgId(request.getOrgId());
        model.setContractId(request.getContractId());
        model.setRateCode(request.getRateCode());
        model.setBasedOnTable(request.getBasedOnTable());
        model.setBasedOnId(request.getBasedOnId());
        model.setParentId(request.getParentId());
        model.setOccurrenceEndTime(request.getOccurrenceEndTime());
        model.setStopTime(request.getStopTime());
        model.setStopperId(request.getStopperId());
        model.setStopPerformerCheckId(request.getStopPerformerCheckId());
        model.setStopCheckTime(request.getStopCheckTime());
        model.setEncounterDiagnosisIds(request.getEncounterDiagnosisIds());
        model.setPrescriptionNo(request.getPrescriptionNo());
        return model;
    }

    /**
     * 定时诊疗是否落在指定发生日：优先 occurrence_start_time，回落开立/签发时刻、created_at。
     *
     * @param request    诊疗申请实体
     * @param filterDate 筛选日期
     * @return 锚点日期与 filterDate 相同为 true
     */
    private boolean matchesTimedServiceOccurrenceDate(ServiceRequest request, LocalDate filterDate) {
        LocalDateTime anchor = request.getOccurrenceStartTime();
        if (anchor == null) {
            anchor = request.getReqOpenedTime();
        }
        if (anchor == null) {
            anchor = request.getReqAuthoredTime();
        }
        if (anchor == null) {
            anchor = request.getCreatedAt();
        }
        return anchor != null && filterDate.equals(anchor.toLocalDate());
    }

    /**
     * 单就诊护理级别候选中解析患者队列展示口径（仅 {@code VERIFIED}）。
     *
     * @param rows          同就诊护理级别医嘱候选（调用方已收窄为 {@code VERIFIED}）
     * @param referenceDate 展示参考自然日
     * @return 读模型；无有效校对护理级别时 null
     */
    private InpatientAutoRollNursingLevelQueryModel resolveNursingLevelForPatientQueue(
        List<ServiceRequest> rows,
        LocalDate referenceDate) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        ServiceRequest verifiedBest = null;
        for (ServiceRequest row : rows) {
            if (row == null || row.getCheckTime() == null) {
                continue;
            }
            // 防御：非 VERIFIED 不展示为当前级别（STOPPED 已退出滚费池）
            if (!RequestStatus.VERIFIED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            LocalDate verifiedDate = row.getCheckTime().toLocalDate();
            // 校对日晚于参考日则参考日尚未生效
            if (verifiedDate.isAfter(referenceDate)) {
                continue;
            }
            if (verifiedBest == null) {
                verifiedBest = row;
                continue;
            }
            if (row.getCheckTime().isAfter(verifiedBest.getCheckTime())) {
                verifiedBest = row;
            }
        }
        if (verifiedBest == null) {
            return null;
        }
        return this.toNursingLevelQueryModel(verifiedBest);
    }

    /**
     * 服务申请实体 → 护理级别读模型（患者队列/滚费共用字段集）。
     *
     * @param request 护理级别服务申请
     * @return 读模型
     */
    private InpatientAutoRollNursingLevelQueryModel toNursingLevelQueryModel(ServiceRequest request) {
        InpatientAutoRollNursingLevelQueryModel model = new InpatientAutoRollNursingLevelQueryModel();
        model.setServiceRequestId(request.getId());
        model.setActivityId(request.getActivityId());
        model.setQuantity(request.getQuantity());
        model.setUnitCode(request.getUnitCode());
        model.setRequesterId(request.getRequesterId());
        model.setCheckTime(request.getCheckTime());
        model.setStatusEnum(request.getStatusEnum());
        return model;
    }

    /**
     * 统计未软删诊疗请求中 {@code encounter_diagnosis_ids} 引用指定就诊诊断行的条数。
     *
     * @param encounterDiagnosisId 就诊诊断行主键
     * @return 引用条数
     */
    public int countReferencingEncounterDiagnosisId(Long encounterDiagnosisId) {
        if (encounterDiagnosisId == null) {
            return 0;
        }
        Specification<ServiceRequest> specification = (root, query, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            EncounterDiagnosisIdsPredicates.containsEncounterDiagnosisId(
                cb, root.get(FieldNameConstants.ENCOUNTER_DIAGNOSIS_IDS), encounterDiagnosisId)
        );
        long count = serviceRequestRepository.count(specification);
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }
}
