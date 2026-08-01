/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院医嘱统一列表骨架（替代 {@code OrderInfoReadModel} 读侧投影行）。
 *
 * <p>{@code orderId} 恒为域 Request 主键（药/诊/材）；{@code orderType} 标识来源表语义。</p>
 * <p>时间字段与三 Request 落库列同名：{@code reqOpenedTime}/{@code reqAuthoredTime}/{@code stopTime}。</p>
 */
@Getter
@Setter
public class InpatientOrderListSkeleton {

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST / DEVICE_REQUEST。 */
    private String orderType;

    /** 域 Request 主键（与 orderType 对应表 id）。 */
    private Long orderId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 请求状态 code。 */
    private String statusEnum;

    /** 诊疗类目 code（仅 SERVICE_REQUEST）。 */
    private String categoryEnum;

    /** 长临 code（LONG / TEMP 等）。 */
    private String therapyEnum;

    /** 开立来源 code（如 DOCTOR_PRESCRIPTION）。 */
    private String generateSourceEnum;

    /** 药品定义主键（药嘱）。 */
    private Long medicationId;

    /** 诊疗活动定义主键（诊嘱）。 */
    private Long activityId;

    /** 耗材定义主键（材嘱）。 */
    private Long deviceDefId;

    /** 开立数量。 */
    private BigDecimal quantity;

    /** 计量单位 code。 */
    private String unitCode;

    /** 开立人主键（药/诊/材统一 {@code requester_id}）。 */
    private Long requesterId;

    /** 药品开立人主键别名（历史兼容；与 {@link #requesterId} 同源）。 */
    private Long medRequesterId;

    /** 开立时间（{@code req_opened_time}）。 */
    private LocalDateTime reqOpenedTime;

    /** 签发时间（{@code req_authored_time}）。 */
    private LocalDateTime reqAuthoredTime;

    /** 停嘱时间（{@code stop_time}）。 */
    private LocalDateTime stopTime;

    /** 停嘱人主键（{@code stopper_id}；未停嘱为 null）。 */
    private Long stopperId;

    /** 执行/发药库位主键（诊嘱）。 */
    private Long locationId;

    /** 药嘱 based_on 表名。 */
    private String basedOnTable;

    /** 药嘱 based_on 主键。 */
    private Long basedOnId;

    /**
     * 负向药嘱锚原药嘱主键（{@code refund_medicine_id}）；正向为 null。
     * <p>门诊退费路径才落此字段；住院退药不造负向 Request。禁止用 {@link #basedOnId} 顶替退单语义。</p>
     */
    private Long refundMedicineId;

    /**
     * 负向诊嘱锚原诊嘱主键（{@code refund_service_id}）；正向为 null。
     * <p>门诊退费路径才落此字段；住院取消执行回退账单，不造负向诊嘱。禁止用 {@link #basedOnId} 顶替。</p>
     */
    private Long refundServiceId;

    /** 材嘱 based_on 表名。 */
    private String devBasedOnTable;

    /** 材嘱 based_on 主键。 */
    private Long devBasedOnId;

    /** 记录创建时间（排序/审计）。 */
    private LocalDateTime createdAt;

    /**
     * 药嘱主键别名（与历史 OrderInfo {@code medReqId} 对齐）。
     *
     * @return 当前行为药嘱时返回 {@link #orderId}，否则 {@code null}
     */
    public Long getMedReqId() {
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(this.orderType)) {
            return this.orderId;
        }
        return null;
    }

    /**
     * 解析开立时刻（业务排序/频次主轴）：优先 {@code reqOpenedTime}，回落 {@code reqAuthoredTime}，再回落 createdAt。
     *
     * @return 开立锚点时刻；均空时 null
     */
    public LocalDateTime resolveOrderOpenedTime() {
        if (this.reqOpenedTime != null) {
            return this.reqOpenedTime;
        }
        if (this.reqAuthoredTime != null) {
            return this.reqAuthoredTime;
        }
        return this.createdAt;
    }

    /**
     * 解析签发时刻（仅追溯/签发锚点，禁止作业务列表排序主轴）：优先 {@code reqAuthoredTime}，回落 {@code reqOpenedTime}。
     *
     * @return 签发锚点时刻；均空时 null
     */
    public LocalDateTime resolveOrderAuthoredTime() {
        if (this.reqAuthoredTime != null) {
            return this.reqAuthoredTime;
        }
        return this.reqOpenedTime;
    }

    /**
     * 药嘱实体 → 列表骨架。
     *
     * @param request 药品申请实体
     * @return 骨架行；id 为空时 null
     */
    public static InpatientOrderListSkeleton fromMedicationRequest(MedicationRequest request) {
        if (request == null || request.getId() == null) {
            return null;
        }
        InpatientOrderListSkeleton skeleton = new InpatientOrderListSkeleton();
        skeleton.setOrderType(ReadModelOrderTypeConstants.MED_REQUEST);
        skeleton.setOrderId(request.getId());
        skeleton.setEncounterId(request.getEncounterId());
        skeleton.setPatientId(request.getPatientId());
        skeleton.setStatusEnum(request.getStatusEnum());
        skeleton.setTherapyEnum(request.getTherapyEnum());
        skeleton.setGenerateSourceEnum(request.getGenerateSourceEnum());
        skeleton.setMedicationId(request.getMedicationId());
        skeleton.setQuantity(request.getQuantity());
        skeleton.setUnitCode(request.getUnitCode());
        skeleton.setRequesterId(request.getRequesterId());
        skeleton.setMedRequesterId(request.getRequesterId());
        skeleton.setReqOpenedTime(request.getReqOpenedTime());
        skeleton.setReqAuthoredTime(request.getReqAuthoredTime());
        skeleton.setStopTime(request.getStopTime());
        skeleton.setStopperId(request.getStopperId());
        skeleton.setBasedOnTable(request.getBasedOnTable());
        skeleton.setBasedOnId(request.getBasedOnId());
        skeleton.setRefundMedicineId(request.getRefundMedicineId());
        skeleton.setCreatedAt(request.getCreatedAt());
        return skeleton;
    }

    /**
     * 药嘱列表读模型 → 列表骨架。
     *
     * @param model 药品申请列表模型
     * @return 骨架行；id 为空时 null
     */
    public static InpatientOrderListSkeleton fromMedicationListModel(MedicationRequestListQueryModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        InpatientOrderListSkeleton skeleton = new InpatientOrderListSkeleton();
        skeleton.setOrderType(ReadModelOrderTypeConstants.MED_REQUEST);
        skeleton.setOrderId(model.getId());
        skeleton.setEncounterId(model.getEncounterId());
        skeleton.setPatientId(model.getPatientId());
        skeleton.setStatusEnum(model.getStatusEnum());
        skeleton.setTherapyEnum(model.getTherapyEnum());
        skeleton.setGenerateSourceEnum(model.getGenerateSourceEnum());
        skeleton.setMedicationId(model.getMedicationId());
        skeleton.setQuantity(model.getQuantity());
        skeleton.setUnitCode(model.getUnitCode());
        Long authorId = model.getRequesterId();
        if (authorId == null) {
            authorId = model.getRecorderId();
        }
        skeleton.setRequesterId(authorId);
        skeleton.setMedRequesterId(authorId);
        skeleton.setReqOpenedTime(model.getReqOpenedTime());
        skeleton.setReqAuthoredTime(model.getReqAuthoredTime());
        skeleton.setStopTime(model.getStopTime());
        skeleton.setStopperId(model.getStopperId());
        skeleton.setBasedOnTable(model.getBasedOnTable());
        skeleton.setBasedOnId(model.getBasedOnId());
        skeleton.setRefundMedicineId(model.getRefundMedicineId());
        return skeleton;
    }

    /**
     * 诊嘱实体 → 列表骨架。
     *
     * @param request 服务申请实体
     * @return 骨架行；id 为空时 null
     */
    public static InpatientOrderListSkeleton fromServiceRequest(ServiceRequest request) {
        if (request == null || request.getId() == null) {
            return null;
        }
        InpatientOrderListSkeleton skeleton = new InpatientOrderListSkeleton();
        skeleton.setOrderType(ReadModelOrderTypeConstants.SERVICE_REQUEST);
        skeleton.setOrderId(request.getId());
        skeleton.setEncounterId(request.getEncounterId());
        skeleton.setPatientId(request.getPatientId());
        skeleton.setStatusEnum(request.getStatusEnum());
        skeleton.setCategoryEnum(request.getCategoryEnum());
        skeleton.setTherapyEnum(request.getTherapyEnum());
        skeleton.setGenerateSourceEnum(request.getGenerateSourceEnum());
        skeleton.setActivityId(request.getActivityId());
        skeleton.setQuantity(request.getQuantity());
        skeleton.setUnitCode(request.getUnitCode());
        skeleton.setRequesterId(request.getRequesterId());
        skeleton.setReqOpenedTime(request.getReqOpenedTime());
        skeleton.setReqAuthoredTime(request.getReqAuthoredTime());
        skeleton.setStopTime(request.getStopTime());
        skeleton.setStopperId(request.getStopperId());
        skeleton.setLocationId(request.getLocationId());
        skeleton.setBasedOnTable(request.getBasedOnTable());
        skeleton.setBasedOnId(request.getBasedOnId());
        skeleton.setRefundServiceId(request.getRefundServiceId());
        skeleton.setCreatedAt(request.getCreatedAt());
        return skeleton;
    }

    /**
     * 诊嘱列表读模型 → 列表骨架。
     *
     * @param model 服务申请列表模型
     * @return 骨架行；id 为空时 null
     */
    public static InpatientOrderListSkeleton fromServiceListModel(ServiceRequestListModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        InpatientOrderListSkeleton skeleton = new InpatientOrderListSkeleton();
        skeleton.setOrderType(ReadModelOrderTypeConstants.SERVICE_REQUEST);
        skeleton.setOrderId(model.getId());
        skeleton.setEncounterId(model.getEncounterId());
        skeleton.setPatientId(model.getPatientId());
        skeleton.setStatusEnum(model.getStatusEnum());
        skeleton.setCategoryEnum(model.getCategoryEnum());
        skeleton.setTherapyEnum(model.getTherapyEnum());
        skeleton.setGenerateSourceEnum(model.getGenerateSourceEnum());
        skeleton.setActivityId(model.getActivityId());
        skeleton.setQuantity(model.getQuantity());
        skeleton.setUnitCode(model.getUnitCode());
        skeleton.setRequesterId(model.getRequesterId());
        skeleton.setReqOpenedTime(model.getReqOpenedTime());
        skeleton.setReqAuthoredTime(model.getReqAuthoredTime());
        skeleton.setStopTime(model.getStopTime());
        skeleton.setStopperId(model.getStopperId());
        skeleton.setLocationId(model.getLocationId());
        skeleton.setBasedOnTable(model.getBasedOnTable());
        skeleton.setBasedOnId(model.getBasedOnId());
        skeleton.setRefundServiceId(model.getRefundServiceId());
        return skeleton;
    }

    /**
     * 材嘱实体 → 列表骨架。
     *
     * @param request 耗材申请实体
     * @return 骨架行；id 为空时 null
     */
    public static InpatientOrderListSkeleton fromDeviceRequest(DeviceRequest request) {
        if (request == null || request.getId() == null) {
            return null;
        }
        InpatientOrderListSkeleton skeleton = new InpatientOrderListSkeleton();
        skeleton.setOrderType(ReadModelOrderTypeConstants.DEVICE_REQUEST);
        skeleton.setOrderId(request.getId());
        skeleton.setEncounterId(request.getEncounterId());
        skeleton.setPatientId(request.getPatientId());
        skeleton.setStatusEnum(request.getStatusEnum());
        skeleton.setGenerateSourceEnum(request.getGenerateSourceEnum());
        skeleton.setDeviceDefId(request.getDeviceDefId());
        skeleton.setQuantity(request.getQuantity());
        skeleton.setUnitCode(request.getUnitCode());
        skeleton.setRequesterId(request.getRequesterId());
        skeleton.setReqOpenedTime(request.getReqOpenedTime());
        skeleton.setReqAuthoredTime(request.getReqAuthoredTime());
        skeleton.setStopTime(request.getStopTime());
        skeleton.setStopperId(request.getStopperId());
        skeleton.setDevBasedOnTable(request.getBasedOnTable());
        skeleton.setDevBasedOnId(request.getBasedOnId());
        skeleton.setCreatedAt(request.getCreatedAt());
        return skeleton;
    }

    /**
     * 材嘱列表读模型 → 列表骨架。
     *
     * @param model 耗材申请列表模型
     * @return 骨架行；id 为空时 null
     */
    public static InpatientOrderListSkeleton fromDeviceListModel(DeviceRequestListModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        InpatientOrderListSkeleton skeleton = new InpatientOrderListSkeleton();
        skeleton.setOrderType(ReadModelOrderTypeConstants.DEVICE_REQUEST);
        skeleton.setOrderId(model.getId());
        skeleton.setEncounterId(model.getEncounterId());
        skeleton.setPatientId(model.getPatientId());
        skeleton.setStatusEnum(model.getStatusEnum());
        skeleton.setGenerateSourceEnum(model.getGenerateSourceEnum());
        skeleton.setDeviceDefId(model.getDeviceDefId());
        skeleton.setQuantity(model.getQuantity());
        skeleton.setUnitCode(model.getUnitCode());
        skeleton.setRequesterId(model.getRequesterId());
        skeleton.setReqOpenedTime(model.getReqOpenedTime());
        skeleton.setReqAuthoredTime(model.getReqAuthoredTime());
        skeleton.setStopTime(model.getStopTime());
        skeleton.setStopperId(model.getStopperId());
        skeleton.setDevBasedOnTable(model.getBasedOnTable());
        skeleton.setDevBasedOnId(model.getBasedOnId());
        return skeleton;
    }
}
