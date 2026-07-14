/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.service;

import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.booking.domain.medtech.result.BookableServiceRequestDto;
import com.openhis.whale.booking.domain.masterdata.service.BookingServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingServiceRequestQueryServiceImpl implements BookingServiceRequestQueryService {

    private static final Set<String> BOOKABLE_CATEGORIES = Set.of(
        "PROOF", "TEST", "ENDOSCOPY", "ORAL_EXAMINATION");

    private final ServiceRequestQueryService serviceRequestQueryService;

    @Override
    public List<BookableServiceRequestDto> listBookableServiceRequestsByEncounter(Long encounterId) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        List<ServiceRequestListModel> rows =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        List<BookableServiceRequestDto> result = new ArrayList<>();
        for (ServiceRequestListModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!this.isBookableCategory(row.getCategoryEnum())) {
                continue;
            }
            if (!RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            BookableServiceRequestDto dto = new BookableServiceRequestDto();
            dto.setServiceRequestId(row.getId());
            dto.setServiceRequestBusNo(serviceRequestQueryService.resolveBusNoByServiceRequestId(row.getId()));
            dto.setActivityName(row.getReasonText());
            dto.setEncounterId(row.getEncounterId());
            dto.setPatientId(row.getPatientId());
            dto.setActivityId(row.getActivityId());
            dto.setCategoryEnum(row.getCategoryEnum());
            dto.setStatusEnum(row.getStatusEnum());
            dto.setReasonText(row.getReasonText());
            result.add(dto);
        }
        return result;
    }

    @Override
    public Long resolveActivityIdByServiceRequestId(Long serviceRequestId) {
        return serviceRequestQueryService.resolveActivityIdByServiceRequestId(serviceRequestId);
    }

    @Override
    public Long resolveEncounterIdByServiceRequestId(Long serviceRequestId) {
        return serviceRequestQueryService.resolveEncounterIdByServiceRequestId(serviceRequestId);
    }

    private boolean isBookableCategory(String categoryEnum) {
        return StringUtils.isNotBlank(categoryEnum) && BOOKABLE_CATEGORIES.contains(categoryEnum.trim());
    }
}
