/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.report;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.PractitionerWorkloadRequestDetailQueryModel;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自三类 Request 批量解析收费明细的开单医生与开立时间（B.6.4：跨聚合读在 application）。
 *
 * <p>ChargeItem {@code enterer_*} 为收费人；开单医生仅取<strong>医生开立</strong>
 * （{@link GenerateSource#DOCTOR_PRESCRIPTION}）Request 的 {@code requester_id}/
 * {@code req_opened_time}。护士划价、自动滚费等非医生开立 Request <strong>不回填</strong>开单医生。</p>
 */
@Component
@RequiredArgsConstructor
public class ChargeItemOrderingDoctorFromRequestSupport {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 选医生时：返回 Request.requester_id 命中的 chargeItem 主键集合。
     *
     * @param chargeItems     候选收费行
     * @param practitionerId  开单医生 practitionerId；空则返回空集合
     * @return 命中主键；无命中为空集合
     */
    public Set<Long> filterChargeItemIdsByOrderingDoctor(
        Collection<ChargeItem> chargeItems,
        Long practitionerId) {
        if (practitionerId == null || chargeItems == null || chargeItems.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Long, OrderingDoctorAnchor> anchorByChargeItemId = this.mapOrderingDoctorAnchorByChargeItemId(chargeItems);
        LinkedHashSet<Long> matchedIds = new LinkedHashSet<>();
        for (Map.Entry<Long, OrderingDoctorAnchor> entry : anchorByChargeItemId.entrySet()) {
            OrderingDoctorAnchor anchor = entry.getValue();
            if (anchor == null || anchor.requesterId() == null) {
                continue;
            }
            if (practitionerId.equals(anchor.requesterId())) {
                matchedIds.add(entry.getKey());
            }
        }
        return matchedIds;
    }

    /**
     * 将开单医生/开立时间回填到工作量明细读模型（字段名仍为 entererId/enteredDate，语义为开单）。
     *
     * <p>导出等全量路径使用；明细分页请拆：{@link #enrichWorkloadDetailOpenedTimeOnly}（全窗排序）+
     * {@link #enrichWorkloadDetailOrderingDoctorOnly}（仅当前页展示）。</p>
     *
     * @param detailRows  明细读模型
     * @param chargeItems 同源收费行（用于按 service 锚点批量读 Request）
     */
    public void enrichWorkloadDetailOrderingDoctorAndOpenedTime(
        List<PractitionerWorkloadRequestDetailQueryModel> detailRows,
        Collection<ChargeItem> chargeItems) {
        if (detailRows == null || detailRows.isEmpty()) {
            return;
        }
        Map<Long, OrderingDoctorAnchor> anchorByChargeItemId = this.mapOrderingDoctorAnchorByChargeItemId(chargeItems);
        this.applyOpenedTimeToWorkloadDetails(detailRows, anchorByChargeItemId);
        this.applyOrderingDoctorToWorkloadDetails(detailRows, anchorByChargeItemId);
    }

    /**
     * 仅为排序回填开立时间（{@code enteredDate} ← {@code req_opened_time}）；不回填开单医生 id。
     *
     * <p>明细分页全窗路径：展开明细后调用本方法再按开立时间 DESC 排序，禁止顺带做展示名 enrich。</p>
     *
     * @param detailRows  明细读模型
     * @param chargeItems 同源收费行
     */
    public void enrichWorkloadDetailOpenedTimeOnly(
        List<PractitionerWorkloadRequestDetailQueryModel> detailRows,
        Collection<ChargeItem> chargeItems) {
        if (detailRows == null || detailRows.isEmpty()) {
            return;
        }
        Map<Long, OrderingDoctorAnchor> anchorByChargeItemId = this.mapOrderingDoctorAnchorByChargeItemId(chargeItems);
        this.applyOpenedTimeToWorkloadDetails(detailRows, anchorByChargeItemId);
    }

    /**
     * 仅为当前页展示回填开单医生 id（{@code entererId} ← {@code requester_id}）。
     *
     * <p>应在排序切片之后、装配医生名之前调用；可复用已解析的 anchor Map 避免二次 IN Request。</p>
     *
     * @param detailRows  当前页明细读模型
     * @param chargeItems 当前页同源收费行（或全窗行，按 chargeItemId 命中）
     */
    public void enrichWorkloadDetailOrderingDoctorOnly(
        List<PractitionerWorkloadRequestDetailQueryModel> detailRows,
        Collection<ChargeItem> chargeItems) {
        if (detailRows == null || detailRows.isEmpty()) {
            return;
        }
        Map<Long, OrderingDoctorAnchor> anchorByChargeItemId = this.mapOrderingDoctorAnchorByChargeItemId(chargeItems);
        this.applyOrderingDoctorToWorkloadDetails(detailRows, anchorByChargeItemId);
    }

    /**
     * 用已解析锚点回填开立时间（不读库）。
     *
     * @param detailRows             明细读模型
     * @param anchorByChargeItemId   chargeItemId → 开单锚点
     */
    public void applyOpenedTimeToWorkloadDetails(
        List<PractitionerWorkloadRequestDetailQueryModel> detailRows,
        Map<Long, OrderingDoctorAnchor> anchorByChargeItemId) {
        if (detailRows == null || detailRows.isEmpty() || anchorByChargeItemId == null) {
            return;
        }
        for (PractitionerWorkloadRequestDetailQueryModel detail : detailRows) {
            if (detail == null || detail.getChargeItemId() == null) {
                continue;
            }
            OrderingDoctorAnchor anchor = anchorByChargeItemId.get(detail.getChargeItemId());
            if (anchor == null) {
                continue;
            }
            detail.setEnteredDate(anchor.openedTime());
        }
    }

    /**
     * 用已解析锚点回填开单医生 id（不读库）。
     *
     * @param detailRows             明细读模型
     * @param anchorByChargeItemId   chargeItemId → 开单锚点
     */
    public void applyOrderingDoctorToWorkloadDetails(
        List<PractitionerWorkloadRequestDetailQueryModel> detailRows,
        Map<Long, OrderingDoctorAnchor> anchorByChargeItemId) {
        if (detailRows == null || detailRows.isEmpty() || anchorByChargeItemId == null) {
            return;
        }
        for (PractitionerWorkloadRequestDetailQueryModel detail : detailRows) {
            if (detail == null || detail.getChargeItemId() == null) {
                continue;
            }
            OrderingDoctorAnchor anchor = anchorByChargeItemId.get(detail.getChargeItemId());
            if (anchor == null) {
                continue;
            }
            detail.setEntererId(anchor.requesterId());
        }
    }

    /**
     * chargeItemId → 开单医生（Request.requester_id）。
     *
     * @param chargeItems 收费行
     * @return 映射；无锚点时不含该键
     */
    public Map<Long, Long> mapOrderingDoctorIdByChargeItemId(Collection<ChargeItem> chargeItems) {
        Map<Long, OrderingDoctorAnchor> anchors = this.mapOrderingDoctorAnchorByChargeItemId(chargeItems);
        LinkedHashMap<Long, Long> result = new LinkedHashMap<>();
        for (Map.Entry<Long, OrderingDoctorAnchor> entry : anchors.entrySet()) {
            if (entry.getValue() != null && entry.getValue().requesterId() != null) {
                result.put(entry.getKey(), entry.getValue().requesterId());
            }
        }
        return result;
    }

    /**
     * 按 service_table/service_id 批量解析开单锚点。
     *
     * @param chargeItems 收费行
     * @return chargeItemId → 开单锚点
     */
    public Map<Long, OrderingDoctorAnchor> mapOrderingDoctorAnchorByChargeItemId(Collection<ChargeItem> chargeItems) {
        LinkedHashMap<Long, OrderingDoctorAnchor> result = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceIds = new LinkedHashSet<>();
        List<ChargeItem> adviceRows = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || chargeItem.getServiceId() == null) {
                continue;
            }
            String serviceTable = chargeItem.getServiceTable();
            if (StringUtils.isEmpty(serviceTable)) {
                continue;
            }
            String normalized = serviceTable.trim();
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)) {
                medicationIds.add(chargeItem.getServiceId());
                adviceRows.add(chargeItem);
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
                serviceIds.add(chargeItem.getServiceId());
                adviceRows.add(chargeItem);
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)) {
                deviceIds.add(chargeItem.getServiceId());
                adviceRows.add(chargeItem);
            }
        }
        Map<Long, MedicationRequest> medicationById = this.loadMedicationRequests(medicationIds);
        Map<Long, ServiceRequestListModel> serviceById = this.serviceRequestQueryService.mapListModelsByIds(serviceIds);
        Map<Long, DeviceRequestListModel> deviceById = this.deviceRequestQueryService.mapListModelsByIds(deviceIds);
        for (ChargeItem chargeItem : adviceRows) {
            String normalized = chargeItem.getServiceTable().trim();
            Long serviceId = chargeItem.getServiceId();
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)) {
                MedicationRequest request = medicationById.get(serviceId);
                // 仅医生开立药嘱回填开单医生；划价/滚费等跳过
                if (request != null && this.isDoctorPrescriptionSource(request.getGenerateSourceEnum())) {
                    result.put(
                        chargeItem.getId(),
                        new OrderingDoctorAnchor(request.getRequesterId(), request.getReqOpenedTime()));
                }
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
                ServiceRequestListModel request = serviceById.get(serviceId);
                if (request != null && this.isDoctorPrescriptionSource(request.getGenerateSourceEnum())) {
                    result.put(
                        chargeItem.getId(),
                        new OrderingDoctorAnchor(request.getRequesterId(), request.getReqOpenedTime()));
                }
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)) {
                DeviceRequestListModel request = deviceById.get(serviceId);
                if (request != null && this.isDoctorPrescriptionSource(request.getGenerateSourceEnum())) {
                    result.put(
                        chargeItem.getId(),
                        new OrderingDoctorAnchor(request.getRequesterId(), request.getReqOpenedTime()));
                }
            }
        }
        return result;
    }

    /**
     * 是否医生开立来源（仅此来源回填开单医生）。
     *
     * @param generateSourceEnum Request {@code generate_source_enum}
     * @return 医生开立时 true
     */
    private boolean isDoctorPrescriptionSource(String generateSourceEnum) {
        if (StringUtils.isEmpty(generateSourceEnum)) {
            return false;
        }
        return GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(generateSourceEnum.trim());
    }

    /**
     * 批量加载药品请求实体。
     *
     * @param ids 药品请求主键
     * @return id → 实体
     */
    private Map<Long, MedicationRequest> loadMedicationRequests(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MedicationRequest> rows = this.medicationRequestQueryService.listByIds(ids);
        LinkedHashMap<Long, MedicationRequest> byId = new LinkedHashMap<>();
        if (rows == null) {
            return byId;
        }
        for (MedicationRequest row : rows) {
            if (row != null && row.getId() != null) {
                byId.put(row.getId(), row);
            }
        }
        return byId;
    }

    /**
     * 开单医生与开立时间锚点。
     *
     * @param requesterId 开单医生
     * @param openedTime  开立时间
     */
    public record OrderingDoctorAnchor(Long requesterId, LocalDateTime openedTime) {
    }
}
