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

package com.openhis.health.module.common.assembler.order;

import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
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

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;

    public List<InpatientOrderListSkeleton> mergeDoctorStationOrders(Long encounterId, String therapyEnum) {
        List<InpatientOrderListSkeleton> merged = new ArrayList<>();
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

    private List<InpatientOrderListSkeleton> sortByCreatedAtDesc(List<InpatientOrderListSkeleton> rows) {
        rows.sort(Comparator.comparing(this::resolveSortTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

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
