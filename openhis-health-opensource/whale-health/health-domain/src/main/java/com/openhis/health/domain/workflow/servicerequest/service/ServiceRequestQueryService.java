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

package com.openhis.health.domain.workflow.servicerequest.service;

import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionBatchItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.service.SpecimenDefinitionQueryService;
import com.openhis.health.domain.diagnosis.bodystructure.service.BodyStructureQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequestDetail;
import com.openhis.health.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.OffsetVerificationModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestHistoryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestDetailRepository;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestQueryService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceRequestDetailRepository serviceRequestDetailRepository;
    private final BodyStructureQueryService bodyStructureQueryService;
    private final SpecimenDefinitionQueryService specimenDefinitionQueryService;


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
        if (encounterId == null || StringUtils.isBlank(prescriptionNo) || StringUtils.isBlank(generateSourceEnum)) {
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
        if (StringUtils.isBlank(statusEnum)) {
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
     * 按主键解析医嘱业务单号（Port 读路径）。
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
     * 按主键解析医嘱所属就诊（Port 读路径）。
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
     * <p>口径：医生开立 + category {@code 26}；须已校对（{@code check_time} 非空）；停嘱后 {@code occurrence_end_time}
     * 早于 billDate 自然日则不再参与；同日内换级取 {@code check_time} 最大者。</p>
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
        Specification<ServiceRequest> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), encounterId),
            cb.equal(root.get(FieldNameConstants.CATEGORY_ENUM), ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING),
            cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), GenerateSource.DOCTOR_PRESCRIPTION.getCode()),
            root.get(FieldNameConstants.STATUS_ENUM).in(
                RequestStatus.VERIFIED.getCode(),
                RequestStatus.STOPPED.getCode()));
        List<ServiceRequest> rows = serviceRequestRepository.findAll(specification);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // Loop：在 billDate 自然日口径下筛选有效护理级别，取 check_time 最大者（换级 stop-old-start-new）。
        ServiceRequest best = null;
        for (ServiceRequest row : rows) {
            if (row == null || row.getCheckTime() == null) {
                continue;
            }
            LocalDate verifiedDate = row.getCheckTime().toLocalDate();
            // 校对日晚于 billDate 的医嘱在 billDate 尚未生效。
            if (verifiedDate.isAfter(billDate)) {
                continue;
            }
            // 停嘱止算：结束自然日早于 billDate 则 billDate 不再计费。
            if (RequestStatus.STOPPED.getCode().equals(row.getStatusEnum())) {
                LocalDateTime endTime = row.getOccurrenceEndTime();
                if (endTime != null && endTime.toLocalDate().isBefore(billDate)) {
                    continue;
                }
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
        InpatientAutoRollNursingLevelQueryModel model = new InpatientAutoRollNursingLevelQueryModel();
        model.setServiceRequestId(best.getId());
        model.setActivityId(best.getActivityId());
        model.setQuantity(best.getQuantity());
        model.setUnitCode(best.getUnitCode());
        model.setRequesterId(best.getRequesterId());
        model.setCheckTime(best.getCheckTime());
        model.setStatusEnum(best.getStatusEnum());
        return Optional.of(model);
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
     * 按主键强制加载诊疗申请列表行。
     */
    public ServiceRequestListModel mustGetListModelById(Long id) {
        return this.toListModel(this.mustGetEntity(id));
    }

    private ServiceRequest mustGetEntity(Long id) {
        return serviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在"));
    }

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
            if (StringUtils.isNotBlank(therapyEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.THERAPY_ENUM), therapyEnum));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        return serviceRequestRepository.findAll(specification, sort);
    }

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

    private List<ServiceRequestListModel> mapListModels(List<ServiceRequest> requests) {
        List<ServiceRequestListModel> models = new ArrayList<>();
        for (ServiceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

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
     * 按服务申请主键批量加载明细行，并解析部位/样本展示名（DM-WKF-004 §6.2.24）。
     *
     * <p>用于医生站申请单列表、统一医嘱列表等读路径；排除逻辑删除明细；未知 {@code target_table} 时
     * {@code targetDisplayName} 为 null。</p>
     *
     * @param serviceRequestIds 服务申请主键集合
     * @return 服务申请主键 → 明细行列表（无明细时该键不出现或映射为空列表，实现采用空列表值）
     */
    public Map<Long, List<ServiceRequestDetailListModel>> mapDetailListModelsByServiceRequestIds(
        Collection<Long> serviceRequestIds) {
        Collection<Long> idSet = this.distinctNonNullServiceRequestIds(serviceRequestIds);
        if (idSet.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ServiceRequestDetail> detailRows = serviceRequestDetailRepository.findByServiceReqIdIn(new ArrayList<>(idSet));
        if (detailRows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> bodyStructureIds = new ArrayList<>();
        List<Long> specimenDefinitionIds = new ArrayList<>();
        for (ServiceRequestDetail detail : detailRows) {
            if (detail == null || Boolean.TRUE.equals(detail.getIsDeleted())) {
                continue;
            }
            if (TableNameConstants.DIA_BODY_STRUCTURE.equals(detail.getTargetTable()) && detail.getTargetId() != null) {
                bodyStructureIds.add(detail.getTargetId());
            } else if (TableNameConstants.ADM_SPECIMEN_DEFINITION.equals(detail.getTargetTable())
                && detail.getTargetId() != null) {
                specimenDefinitionIds.add(detail.getTargetId());
            }
        }
        Map<Long, String> bodyStructureNameMap = bodyStructureQueryService.mapNamesByIds(bodyStructureIds);
        Map<Long, String> specimenNameMap = this.mapSpecimenNamesByIds(specimenDefinitionIds);
        Map<Long, List<ServiceRequestDetailListModel>> grouped = new HashMap<>();
        for (ServiceRequestDetail detail : detailRows) {
            if (detail == null || detail.getServiceReqId() == null || Boolean.TRUE.equals(detail.getIsDeleted())) {
                continue;
            }
            ServiceRequestDetailListModel model = new ServiceRequestDetailListModel();
            model.setId(detail.getId());
            model.setServiceReqId(detail.getServiceReqId());
            model.setTargetTable(detail.getTargetTable());
            model.setTargetId(detail.getTargetId());
            model.setRemark(detail.getRemark());
            model.setTargetDisplayName(this.resolveDetailTargetDisplayName(
                detail.getTargetTable(), detail.getTargetId(), bodyStructureNameMap, specimenNameMap));
            grouped.computeIfAbsent(detail.getServiceReqId(), key -> new ArrayList<>()).add(model);
        }
        return grouped;
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
     * 批量解析样本定义展示名。
     *
     * @param specimenDefinitionIds 样本定义主键集合
     * @return id → 名称
     */
    private Map<Long, String> mapSpecimenNamesByIds(List<Long> specimenDefinitionIds) {
        Map<Long, String> result = new HashMap<>();
        if (specimenDefinitionIds == null || specimenDefinitionIds.isEmpty()) {
            return result;
        }
        List<SpecimenDefinitionBatchItemQueryModel> rows = specimenDefinitionQueryService.listByIds(specimenDefinitionIds);
        for (SpecimenDefinitionBatchItemQueryModel row : rows) {
            if (row != null && row.getId() != null) {
                result.put(row.getId(), row.getName());
            }
        }
        return result;
    }

    /**
     * 按 target 白名单解析明细目标展示名。
     *
     * @param targetTable 目标表名
     * @param targetId 目标主键
     * @param bodyStructureNameMap 部位名称映射
     * @param specimenNameMap 样本名称映射
     * @return 展示名或 null
     */
    private String resolveDetailTargetDisplayName(
        String targetTable,
        Long targetId,
        Map<Long, String> bodyStructureNameMap,
        Map<Long, String> specimenNameMap) {
        if (targetId == null || targetTable == null) {
            return null;
        }
        if (TableNameConstants.DIA_BODY_STRUCTURE.equals(targetTable)) {
            return bodyStructureNameMap.get(targetId);
        }
        if (TableNameConstants.ADM_SPECIMEN_DEFINITION.equals(targetTable)) {
            return specimenNameMap.get(targetId);
        }
        return null;
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
        model.setReqAuthoredTime(request.getAuthoredTime());
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
        model.setConditionDefinitionId(request.getConditionDefinitionId());
        model.setEncounterDiagnosisId(request.getEncounterDiagnosisId());
        model.setPrescriptionNo(request.getPrescriptionNo());
        return model;
    }

    private boolean matchesTimedServiceOccurrenceDate(ServiceRequest request, LocalDate filterDate) {
        LocalDateTime anchor = request.getOccurrenceStartTime();
        if (anchor == null) {
            anchor = request.getAuthoredTime();
        }
        if (anchor == null) {
            anchor = request.getCreatedAt();
        }
        return anchor != null && filterDate.equals(anchor.toLocalDate());
    }
}
