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

package com.openhis.health.module.querysupport.pharmacy;

import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.supplydelivery.queries.model.SupplyDeliverySlipHeaderQueryModel;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院药品汇总单读编排：SupplyRequest + SupplyDelivery 跨聚合组装（B.6.1.3）。
 */
@Component
@RequiredArgsConstructor
public class MedicationSummarySlipQuerySupport {

    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;

    /**
     * IF-IP-SUM-02：药品汇总单头分页（type 27）。
     *
     * @param query 位置/时段/关键字收窄；为 null 时返回空页
     * @return 汇总单头分页（含 SupplyDelivery 发放态）
     */
    public Page<MedicationSummarySlipQueryModel> pageMedicationSummarySlips(SupplyRequestDocumentListQuery query) {
        if (query == null) {
            return Page.empty();
        }
        if (query.getTypeEnum() == null || query.getTypeEnum().isBlank()) {
            query.setTypeEnum(SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
        }
        long total = supplyRequestQueryService.countDistinctBusNo(query);
        List<String> busNos = supplyRequestQueryService.listDistinctBusNoPage(query);
        if (busNos.isEmpty()) {
            int springIndex = query.springPageIndex();
            int pageSize = query.getBoundedPageSize();
            return new PageImpl<>(
                Collections.emptyList(),
                PageRequest.of(springIndex, pageSize),
                total);
        }
        List<SupplyRequestModel> detailRows = supplyRequestQueryService.listByBusNosWithFilter(query, busNos);
        List<MedicationSummarySlipQueryModel> headers = this.assembleMedicationSummarySlipHeaders(detailRows);
        int springIndex = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        return new PageImpl<>(headers, PageRequest.of(springIndex, pageSize), total);
    }

    private List<MedicationSummarySlipQueryModel> assembleMedicationSummarySlipHeaders(List<SupplyRequestModel> detailRows) {
        if (detailRows == null || detailRows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<SupplyRequestModel>> rowsByBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel row : detailRows) {
            if (row == null || row.getBusNo() == null) {
                continue;
            }
            rowsByBusNo.computeIfAbsent(row.getBusNo(), key -> new ArrayList<>()).add(row);
        }
        List<Long> requestIds = new ArrayList<>();
        for (SupplyRequestModel row : detailRows) {
            if (row != null && row.getId() != null) {
                requestIds.add(row.getId());
            }
        }
        Map<Long, List<SupplyDeliverySlipHeaderQueryModel>> deliveriesByRequestId =
            this.indexDeliveriesByRequestId(
                supplyDeliveryQueryService.listDeliveriesForSummarySlipHeaders(requestIds));
        List<MedicationSummarySlipQueryModel> headers = new ArrayList<>();
        for (Map.Entry<String, List<SupplyRequestModel>> entry : rowsByBusNo.entrySet()) {
            headers.add(this.toMedicationSummarySlipHeader(entry.getKey(), entry.getValue(), deliveriesByRequestId));
        }
        return headers;
    }

    private Map<Long, List<SupplyDeliverySlipHeaderQueryModel>> indexDeliveriesByRequestId(
        List<SupplyDeliverySlipHeaderQueryModel> deliveries) {
        Map<Long, List<SupplyDeliverySlipHeaderQueryModel>> result = new HashMap<>();
        if (deliveries == null) {
            return result;
        }
        for (SupplyDeliverySlipHeaderQueryModel delivery : deliveries) {
            if (delivery == null || delivery.getRequestId() == null) {
                continue;
            }
            result.computeIfAbsent(delivery.getRequestId(), key -> new ArrayList<>()).add(delivery);
        }
        return result;
    }

    private MedicationSummarySlipQueryModel toMedicationSummarySlipHeader(
        String busNo,
        List<SupplyRequestModel> rows,
        Map<Long, List<SupplyDeliverySlipHeaderQueryModel>> deliveriesByRequestId) {
        MedicationSummarySlipQueryModel header = new MedicationSummarySlipQueryModel();
        header.setBusNo(busNo);
        LocalDateTime maxApplyTime = null;
        Long purposeLocationId = null;
        Long applicantId = null;
        for (SupplyRequestModel row : rows) {
            if (row == null) {
                continue;
            }
            if (purposeLocationId == null) {
                purposeLocationId = row.getPurposeLocationId();
            }
            if (applicantId == null) {
                applicantId = row.getApplicantId();
            }
            if (row.getApplyTime() != null && (maxApplyTime == null || row.getApplyTime().isAfter(maxApplyTime))) {
                maxApplyTime = row.getApplyTime();
            }
        }
        header.setApplyTime(maxApplyTime);
        header.setPurposeLocationId(purposeLocationId);
        header.setApplicantId(applicantId);
        String supplyStatusEnum = null;
        for (SupplyRequestModel row : rows) {
            if (row != null && row.getStatusEnum() != null && !row.getStatusEnum().isBlank()) {
                supplyStatusEnum = row.getStatusEnum();
                break;
            }
        }
        header.setSupplyStatusEnum(supplyStatusEnum);
        Long receiverId = null;
        String deliveryStatusEnum = null;
        LocalDateTime dispenseTime = null;
        for (SupplyRequestModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            List<SupplyDeliverySlipHeaderQueryModel> deliveries = deliveriesByRequestId.get(row.getId());
            if (deliveries == null) {
                continue;
            }
            for (SupplyDeliverySlipHeaderQueryModel delivery : deliveries) {
                if (delivery == null) {
                    continue;
                }
                if (receiverId == null && delivery.getReceiverId() != null) {
                    receiverId = delivery.getReceiverId();
                }
                if (deliveryStatusEnum == null && delivery.getStatusEnum() != null) {
                    deliveryStatusEnum = delivery.getStatusEnum();
                }
                if (this.isSummaryDispensedStatus(delivery.getStatusEnum()) && delivery.getReceiveTime() != null) {
                    if (dispenseTime == null || delivery.getReceiveTime().isAfter(dispenseTime)) {
                        dispenseTime = delivery.getReceiveTime();
                    }
                }
            }
        }
        header.setReceiverId(receiverId);
        header.setDeliveryStatusEnum(deliveryStatusEnum);
        header.setDispenseTime(dispenseTime);
        return header;
    }

    private boolean isSummaryDispensedStatus(String statusEnum) {
        if (statusEnum == null || statusEnum.isBlank()) {
            return false;
        }
        if (DispenseStatus.COMPLETED.getCode().equals(statusEnum)) {
            return true;
        }
        return DispenseStatus.PART_COMPLETED.getCode().equals(statusEnum);
    }
}
