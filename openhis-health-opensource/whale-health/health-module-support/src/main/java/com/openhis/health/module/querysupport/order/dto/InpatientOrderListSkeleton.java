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

package com.openhis.health.module.querysupport.order.dto;

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
 */
@Getter
@Setter
public class InpatientOrderListSkeleton {

    private String orderType;
    private Long orderId;
    private Long encounterId;
    private Long patientId;
    private String statusEnum;
    private String categoryEnum;
    private String therapyEnum;
    private String generateSourceEnum;
    private Long medicationId;
    private Long activityId;
    private Long deviceDefId;
    private BigDecimal quantity;
    private String unitCode;
    private Long requesterId;
    private Long medRequesterId;
    private LocalDateTime authoredTime;
    private LocalDateTime medAuthoredTime;
    private LocalDateTime effectiveDoseEnd;
    private Long locationId;
    private String basedOnTable;
    private Long basedOnId;
    private String devBasedOnTable;
    private Long devBasedOnId;
    private LocalDateTime createdAt;

    /** 药嘱主键别名（与历史 OrderInfo {@code medReqId} 对齐）。 */
    public Long getMedReqId() {
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(this.orderType)) {
            return this.orderId;
        }
        return null;
    }

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
        skeleton.setMedRequesterId(request.getRequesterId());
        skeleton.setMedAuthoredTime(request.getReqAuthoredTime());
        skeleton.setEffectiveDoseEnd(request.getEffectiveDoseEnd());
        skeleton.setBasedOnTable(request.getBasedOnTable());
        skeleton.setBasedOnId(request.getBasedOnId());
        skeleton.setCreatedAt(request.getCreatedAt());
        return skeleton;
    }

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
        skeleton.setMedRequesterId(authorId);
        skeleton.setMedAuthoredTime(model.getReqAuthoredTime());
        skeleton.setEffectiveDoseEnd(model.getEffectiveDoseEnd());
        skeleton.setBasedOnTable(model.getBasedOnTable());
        skeleton.setBasedOnId(model.getBasedOnId());
        return skeleton;
    }

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
        skeleton.setAuthoredTime(request.getAuthoredTime());
        skeleton.setLocationId(request.getLocationId());
        skeleton.setBasedOnTable(request.getBasedOnTable());
        skeleton.setBasedOnId(request.getBasedOnId());
        skeleton.setCreatedAt(request.getCreatedAt());
        return skeleton;
    }

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
        skeleton.setAuthoredTime(model.getReqAuthoredTime());
        skeleton.setLocationId(model.getLocationId());
        skeleton.setBasedOnTable(model.getBasedOnTable());
        skeleton.setBasedOnId(model.getBasedOnId());
        return skeleton;
    }

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
        skeleton.setAuthoredTime(request.getReqAuthoredTime());
        skeleton.setDevBasedOnTable(request.getBasedOnTable());
        skeleton.setDevBasedOnId(request.getBasedOnId());
        skeleton.setCreatedAt(request.getCreatedAt());
        return skeleton;
    }

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
        skeleton.setAuthoredTime(model.getReqAuthoredTime());
        skeleton.setDevBasedOnTable(model.getBasedOnTable());
        skeleton.setDevBasedOnId(model.getBasedOnId());
        return skeleton;
    }
}
