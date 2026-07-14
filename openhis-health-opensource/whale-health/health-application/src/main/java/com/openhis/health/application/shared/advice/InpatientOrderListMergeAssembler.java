/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 住院医嘱列表聚合（P4-DOM-005：应用层 merge/sort，禁止 ClinicalOrder 横切门面）。
 */
@Component
@RequiredArgsConstructor
public class InpatientOrderListMergeAssembler {

    /** 药品申请列表只读查询。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 诊疗申请列表只读查询。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 耗材申请列表只读查询。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 住院医生站医嘱列表：药品 + 诊疗合并后按开立时间倒序。
     *
     * @param encounterId  就诊主键
     * @param therapyEnum  长临筛选 code；可 null 表示不限
     * @return 合并后的列表骨架
     */
    public List<InpatientOrderListSkeleton> mergeDoctorStationOrders(Long encounterId, String therapyEnum) {
        List<InpatientOrderListSkeleton> merged = new ArrayList<>();
        // Pre：分别拉取药品、诊疗住院医生站列表行
        for (MedicationRequestListQueryModel row : medicationRequestQueryService.listInpatientDoctorStationOrders(
            encounterId, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromMedicationListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        for (ServiceRequestListModel row : serviceRequestQueryService.listInpatientDoctorStationOrders(
            encounterId, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromServiceListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        return this.sortByCreatedAtDesc(merged);
    }

    /**
     * 护士站医嘱审核列表：药品 + 诊疗合并后按开立时间倒序。
     *
     * @param encounterIds  就诊主键列表
     * @param statusEnumIn  请求状态 IN 筛选
     * @param therapyEnum   长临筛选 code
     * @return 合并后的列表骨架
     */
    public List<InpatientOrderListSkeleton> mergeNurseStationAuditOrders(
        List<Long> encounterIds, List<String> statusEnumIn, String therapyEnum) {
        List<InpatientOrderListSkeleton> merged = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRequestQueryService.listInpatientNurseStationAuditOrders(
            encounterIds, statusEnumIn, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromMedicationListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        for (ServiceRequestListModel row : serviceRequestQueryService.listInpatientNurseStationAuditOrders(
            encounterIds, statusEnumIn, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromServiceListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        return this.sortByCreatedAtDesc(merged);
    }

    /**
     * 护士站医嘱执行列表：药品 + 诊疗合并后按开立时间倒序。
     *
     * @param encounterIds 就诊主键列表
     * @param therapyEnum  长临筛选 code
     * @return 合并后的列表骨架
     */
    public List<InpatientOrderListSkeleton> mergeNurseStationExecuteOrders(
        List<Long> encounterIds, String therapyEnum) {
        List<InpatientOrderListSkeleton> merged = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRequestQueryService.listInpatientNurseStationExecuteOrders(
            encounterIds, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromMedicationListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        for (ServiceRequestListModel row : serviceRequestQueryService.listInpatientNurseStationExecuteOrders(
            encounterIds, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromServiceListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        return this.sortByCreatedAtDesc(merged);
    }

    /**
     * 护士站补录医嘱列表：药品 + 诊疗合并后按开立时间倒序。
     *
     * @param encounterIds 就诊主键列表
     * @param therapyEnum  长临筛选 code
     * @return 合并后的列表骨架
     */
    public List<InpatientOrderListSkeleton> mergeNurseStationSupplementOrders(
        List<Long> encounterIds, String therapyEnum) {
        List<InpatientOrderListSkeleton> merged = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRequestQueryService.listInpatientNurseStationSupplementOrders(
            encounterIds, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromMedicationListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        for (ServiceRequestListModel row : serviceRequestQueryService.listInpatientNurseStationSupplementOrders(
            encounterIds, therapyEnum)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromServiceListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        return this.sortByCreatedAtDesc(merged);
    }

    /**
     * 护士站划价列表：诊疗 + 耗材合并后按开立时间倒序。
     *
     * @param encounterIds     就诊主键列表
     * @param pricedTimeFrom   划价时间起
     * @param pricedTimeTo     划价时间止
     * @param parentBasedOnId  父医嘱主键筛选
     * @return 合并后的列表骨架
     */
    public List<InpatientOrderListSkeleton> mergeNurseStationPricedOrders(
        List<Long> encounterIds,
        LocalDateTime pricedTimeFrom,
        LocalDateTime pricedTimeTo,
        Long parentBasedOnId) {
        List<InpatientOrderListSkeleton> merged = new ArrayList<>();
        for (ServiceRequestListModel row : serviceRequestQueryService.listInpatientNurseStationPricedOrders(
            encounterIds, pricedTimeFrom, pricedTimeTo, parentBasedOnId)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromServiceListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        for (DeviceRequestListModel row : deviceRequestQueryService.listInpatientNurseStationPricedOrders(
            encounterIds, pricedTimeFrom, pricedTimeTo, parentBasedOnId)) {
            InpatientOrderListSkeleton skeleton = InpatientOrderListSkeleton.fromDeviceListModel(row);
            if (skeleton != null) {
                merged.add(skeleton);
            }
        }
        return this.sortByCreatedAtDesc(merged);
    }

    /**
     * 医嘱列表骨架按创建/开嘱时间降序排序（就地将序）。
     *
     * @param rows 待排序列表
     * @return 同一列表引用
     */
    private List<InpatientOrderListSkeleton> sortByCreatedAtDesc(List<InpatientOrderListSkeleton> rows) {
        rows.sort(Comparator.comparing(this::resolveSortTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    /**
     * 列表排序键：createdAt → medAuthoredTime → authoredTime。
     *
     * @param row 列表骨架
     * @return 排序时刻；row 为 null 时 null
     */
    private LocalDateTime resolveSortTime(InpatientOrderListSkeleton row) {
        if (row == null) {
            return null;
        }
        if (row.getCreatedAt() != null) {
            return row.getCreatedAt();
        }
        if (row.getMedAuthoredTime() != null) {
            return row.getMedAuthoredTime();
        }
        return row.getAuthoredTime();
    }
}
