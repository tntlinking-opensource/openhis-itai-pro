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

package com.openhis.health.domain.workflow.devicerequest.service;

import com.openhis.health.domain.workflow.devicerequest.repository.DeviceRequestRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestFifoSignContext;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestHistoryModel;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestQueryModel;
import com.openhis.health.domain.workflow.devicerequest.queries.model.OffsetVerificationModel;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 耗材请求读侧查询服务。
 *
 * <p>约束：只读查询，不写库；默认过滤软删。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceRequestQueryService {

    private final DeviceRequestRepository deviceRequestRepository;


    /**
     * 按主键批量加载耗材请求列表行（护士站已划价列表数量/单位回源）。
     *
     * @param deviceRequestIds 耗材请求主键集合
     * @return 主键 → 列表行
     */
    public Map<Long, DeviceRequestListModel> mapListModelsByIds(Collection<Long> deviceRequestIds) {
        if (deviceRequestIds == null || deviceRequestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : deviceRequestIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DeviceRequest> rows = deviceRequestRepository.findAllById(distinctIds);
        Map<Long, DeviceRequestListModel> map = new HashMap<>();
        for (DeviceRequest request : rows) {
            if (request == null || request.getId() == null || Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            map.put(request.getId(), this.toListModel(request));
        }
        return map;
    }

    /**
     * 必取耗材请求。
     */
    public DeviceRequestQueryModel mustGetDeviceRequest(Long id) {
        DeviceRequest request = deviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在"));
        DeviceRequestQueryModel model = new DeviceRequestQueryModel();
        model.setId(request.getId());
        model.setStatusEnum(request.getStatusEnum());
        model.setQuantity(request.getQuantity());
        model.setRefundDeviceId(request.getRefundDeviceId());
        return model;
    }

    /**
     * 加载门诊医生站耗材签发后 FIFO 发放编排所需的请求字段切片。
     *
     * @param deviceReqId 耗材请求主键
     * @return 只读上下文
     * @throws BusinessException 请求不存在时
     */
    public DeviceRequestFifoSignContext loadFifoSignContextOrThrow(Long deviceReqId) {
        DeviceRequest request = deviceRequestRepository.findById(deviceReqId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在"));
        DeviceRequestFifoSignContext ctx = new DeviceRequestFifoSignContext();
        ctx.setDeviceReqId(request.getId());
        ctx.setDeviceDefId(request.getDeviceDefId());
        ctx.setEncounterId(request.getEncounterId());
        ctx.setPatientId(request.getPatientId());
        ctx.setQuantity(request.getQuantity());
        ctx.setUnitCode(request.getUnitCode());
        return ctx;
    }

    /**
     * 判断耗材是否存在非终态医嘱申请（调价提交互斥 BR-PRC-SUB-003：在途医嘱）。
     *
     * @param deviceDefId 耗材定义主键 {@code adm_device_definition.id}
     * @return 存在在途申请时为 true
     */
    public boolean existsInTransitRequestByDeviceDefId(Long deviceDefId) {
        if (deviceDefId == null) {
            return false;
        }
        // 非终态申请（待发、已签发、已校对、暂挂）视为在途医嘱
        Specification<DeviceRequest> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.DEVICE_DEF_ID), deviceDefId));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(this.inTransitRequestStatusCodes()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return deviceRequestRepository.count(specification) > 0;
    }

    /**
     * 在途耗材申请状态码：待发、已签发、已校对、暂挂（非 COMPLETED/CANCELLED/STOPPED）。
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

    /**
     * 按就诊查询耗材请求。
     */
    public List<DeviceRequestListModel> listDeviceRequestsByEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "按就诊查询耗材请求时 encounterId 不能为空");
        }
        List<DeviceRequest> requests = deviceRequestRepository.findByEncounterId(encounterId);
        List<DeviceRequestListModel> models = new ArrayList<>();
        for (DeviceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 门诊医生站：按就诊与生成来源过滤耗材请求（与药品列表对称，AS-OP-DOR-003 BR-ORD-010）。
     *
     * @param encounterId 就诊标识
     * @param generateSourceEnum 生成来源码
     * @return 列表
     */
    public List<DeviceRequestListModel> listDeviceRequestsByEncounterForDoctorStation(Long encounterId, String generateSourceEnum) {
        if (encounterId == null || generateSourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "按医生站来源过滤耗材请求时 encounterId 与 generateSourceEnum 不能为空");
        }
        List<DeviceRequest> requests = deviceRequestRepository.findByEncounterIdAndGenerateSourceEnum(encounterId, generateSourceEnum);
        List<DeviceRequestListModel> models = new ArrayList<>();
        for (DeviceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 按来源锚点查询绑定耗材请求列表（取消执行 based_on 反查，DM-WKF-003 §6.2.10）。
     *
     * @param basedOnTable 来源表名
     * @param basedOnId    来源主键
     * @return 耗材请求列表
     */
    public List<DeviceRequestListModel> listDeviceRequestsByBasedOnAnchor(String basedOnTable, Long basedOnId) {
        if (basedOnId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "按来源锚点查询耗材请求时 basedOnId 不能为空");
        }
        if (basedOnTable == null) {
            basedOnTable = "";
        }
        List<DeviceRequest> requests = deviceRequestRepository.findByBasedOnTableAndBasedOnId(basedOnTable, basedOnId);
        List<DeviceRequestListModel> models = new ArrayList<>();
        for (DeviceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 按来源表名与多条来源主键批量查询绑定耗材请求。
     *
     * @param basedOnTable 来源表名
     * @param basedOnIds   来源主键集合
     * @return 耗材请求列表
     */
    public List<DeviceRequestListModel> listDeviceRequestsByBasedOnAnchors(String basedOnTable, Collection<Long> basedOnIds) {
        if (basedOnIds == null || basedOnIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "批量按来源锚点查询耗材请求时 basedOnIds 不能为空");
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
        List<DeviceRequest> requests = deviceRequestRepository.findByBasedOnTableAndBasedOnIdIn(basedOnTable, distinctIds);
        List<DeviceRequestListModel> models = new ArrayList<>();
        for (DeviceRequest request : requests) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 列表行映射。
     *
     * @param request 实体
     * @return 列表模型
     */
    private DeviceRequestListModel toListModel(DeviceRequest request) {
        DeviceRequestListModel model = new DeviceRequestListModel();
        model.setId(request.getId());
        model.setDeviceDefId(request.getDeviceDefId());
        model.setStatusEnum(request.getStatusEnum());
        model.setRefundDeviceId(request.getRefundDeviceId());
        model.setEncounterId(request.getEncounterId());
        model.setPatientId(request.getPatientId());
        model.setBusNo(request.getBusNo());
        model.setGenerateSourceEnum(request.getGenerateSourceEnum());
        model.setReqAuthoredTime(request.getReqAuthoredTime());
        model.setSortNumber(request.getSortNumber());
        model.setRequesterId(request.getRequesterId());
        model.setQuantity(request.getQuantity());
        model.setUnitCode(request.getUnitCode());
        model.setLotNumber(request.getLotNumber());
        model.setRemark(request.getRemark());
        model.setPerformLocation(request.getPerformLocation());
        model.setLocationId(request.getLocationId());
        model.setRateCode(request.getRateCode());
        model.setDeviceSpecifications(request.getDeviceSpecifications());
        model.setBasedOnTable(request.getBasedOnTable());
        model.setBasedOnId(request.getBasedOnId());
        model.setConditionDefinitionId(request.getConditionDefinitionId());
        model.setEncounterDiagnosisId(request.getEncounterDiagnosisId());
        return model;
    }

    /**
     * 医生站历史医嘱：在患者范围内收集存在医生开立耗材请求的就诊主键（排除当前就诊）。
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
        Specification<DeviceRequest> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.ENCOUNTER_ID), excludeEncounterId));
            predicates.add(cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), doctorSource));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<DeviceRequest> rows = deviceRequestRepository.findAll(specification);
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // Loop：从医生开立耗材行提取就诊 id，LinkedHashSet 保序去重。
        for (DeviceRequest row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        return new ArrayList<>(encounterIds);
    }

    /**
     * 查询患者历史耗材请求（排除当前就诊）。
     */
    public List<DeviceRequestHistoryModel> listDeviceRequestHistoryByPatient(Long patientId, Long excludeEncounterId) {
        if (patientId == null || excludeEncounterId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "查询患者历史耗材请求时 patientId/excludeEncounterId 不能为空");
        }
        List<DeviceRequest> requests = deviceRequestRepository.findByPatientIdAndEncounterIdNot(patientId, excludeEncounterId);
        List<DeviceRequestHistoryModel> models = new ArrayList<>();
        for (DeviceRequest request : requests) {
            DeviceRequestHistoryModel model = new DeviceRequestHistoryModel();
            model.setId(request.getId());
            model.setPatientId(request.getPatientId());
            model.setEncounterId(request.getEncounterId());
            model.setStatusEnum(request.getStatusEnum());
            models.add(model);
        }
        return models;
    }

    /**
     * 断言可删除（门控）：仅草稿态（statusEnum=1）的耗材请求允许删除操作，结果供应用服务层读取后决策。
     *
     * @param id 耗材请求主键
     * @return 可删除时返回 true
     * @throws BusinessException 请求不存在
     */
    public Boolean assertDeviceRequestDeletable(Long id) {
        DeviceRequest request = deviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在"));
        // 委托聚合根 isDraftEditable() 判断草稿状态，替代魔数直接比较（B.10.3）。
        return request.isDraftEditable();
    }

    /**
     * 断言可签退（门控）：仅已发送态（statusEnum=2）的耗材请求允许签退操作，结果供应用服务层读取后决策。
     *
     * @param id 耗材请求主键
     * @return 可签退时返回 true
     * @throws BusinessException 请求不存在
     */
    public Boolean assertDeviceRequestSignOffAllowed(Long id) {
        DeviceRequest request = deviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在"));
        // 委托聚合根 isSignOffAllowed() 判断已发送状态，替代魔数直接比较（B.10.3）。
        return request.isSignOffAllowed();
    }

    /**
     * 冲销校验（offsetVerification）。
     *
     * <p>权威口径：已冲销数量 = refundDeviceId=原单 且 quantity&lt;0 的历史行 quantity 绝对值之和。</p>
     */
    public OffsetVerificationModel offsetVerification(Long sourceRequestId, BigDecimal preOffsetQuantity) {
        if (sourceRequestId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "冲销校验时 sourceRequestId 不能为空");
        }
        // 未传本单拟冲数量时按 0 处理，仅与已登记负向行比对
        if (preOffsetQuantity == null) {
            preOffsetQuantity = BigDecimal.ZERO;
        }

        DeviceRequest source = deviceRequestRepository.findById(sourceRequestId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "原耗材请求不存在"));

        List<DeviceRequest> negativeRows = deviceRequestRepository.findByRefundDeviceIdAndQuantityLessThan(
            sourceRequestId, BigDecimal.ZERO);
        BigDecimal offsetQuantity = BigDecimal.ZERO;
        for (DeviceRequest row : negativeRows) {
            if (row.getQuantity() == null) {
                continue;
            }
            offsetQuantity = offsetQuantity.add(row.getQuantity().abs());
        }

        BigDecimal remaining = source.getQuantity().subtract(offsetQuantity).subtract(preOffsetQuantity);

        OffsetVerificationModel model = new OffsetVerificationModel();
        model.setSourceRequestId(sourceRequestId);
        model.setSourceQuantity(source.getQuantity());
        model.setOffsetQuantity(offsetQuantity);
        model.setRemainingQuantity(remaining);
        return model;
    }

    /**
     * 退费申请前可退断言（BR-DR-009 最小门控）。
     *
     * <p>当前仅校验耗材请求状态是否为已完成（statusEnum=3）；DM-WKF-003 §6.2.7 + BR-DR-009 原文
     * 要求同时结合发放状态与费用状态等多维度校验，跨聚合部分需由应用服务层叠加完成。</p>
     *
     * <p>// DOC-CONFLICT: BR-DR-009 要求跨域校验（发放状态、费用状态），领域层无法访问其他聚合仓储；
     * 应用服务层须在调用本方法后自行补充跨域校验，此处仅作最小状态门控占位。</p>
     *
     * @param id 耗材请求主键
     * @return 状态满足退费前置条件时返回 true
     * @throws BusinessException 请求不存在
     */
    public Boolean assertDeviceRefundableBeforePaymentRefundRequested(Long id) {
        DeviceRequest request = deviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在"));
        // 委托聚合根 isCompleted() 判断已完成状态，替代魔数直接比较（B.10.3）。
        return request.isCompleted();
    }

    /**
     * 按原耗材请求主键批量查询关联退耗/负向请求（DM-WKF-003 §6.2.8；AS-OP-FIN-003 IF-REF-04）。
     *
     * @param refundDeviceIds 原耗材请求主键集合（{@code refund_device_id} 指向方）
     * @return 命中关联字段的请求行；入参为空时返回空列表
     */
    public List<DeviceRequest> listByRefundDeviceIdIn(Collection<Long> refundDeviceIds) {
        if (refundDeviceIds == null || refundDeviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : refundDeviceIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return this.deviceRequestRepository.findByRefundDeviceIdIn(ids);
    }

    /**
     * 住院护士站已划价耗材请求列表（替代 OrderInfo RM Q-IP-PRC-001 耗材子集）。
     */
    public List<DeviceRequestListModel> listInpatientNurseStationPricedOrders(
        List<Long> encounterIds,
        LocalDateTime pricedTimeFrom,
        LocalDateTime pricedTimeTo,
        Long parentBasedOnId) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT,
                "住院护士站已划价耗材列表查询时 encounterIds 不能为空");
        }
        Specification<DeviceRequest> specification = (root, criteriaQuery, cb) -> {
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
        List<DeviceRequest> rows = deviceRequestRepository.findAll(specification, sort);
        List<DeviceRequestListModel> models = new ArrayList<>();
        for (DeviceRequest request : rows) {
            models.add(this.toListModel(request));
        }
        return models;
    }

    /**
     * 按主键加载耗材请求列表行（供按表路由）。
     */
    public DeviceRequestListModel getListModelById(Long id) {
        if (id == null) {
            return null;
        }
        DeviceRequest request = deviceRequestRepository.findById(id).orElse(null);
        if (request == null || Boolean.TRUE.equals(request.getIsDeleted())) {
            return null;
        }
        return this.toListModel(request);
    }

    /**
     * 按主键强制加载耗材请求列表行。
     */
    public DeviceRequestListModel mustGetListModelById(Long id) {
        DeviceRequest request = deviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在"));
        return this.toListModel(request);
    }
}
