/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.mapper;

import com.openhis.health.application.peis.orchestration.command.AdjustPeServiceRequestsCommand;
import com.openhis.health.application.peis.orchestration.command.RegisterPersonalCheckInCommand;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.application.peis.registration.dto.CustomerRowDto;
import com.openhis.health.application.peis.registration.dto.ServiceRequestLineDto;
import com.openhis.health.application.peis.registration.request.AdjustServiceRequestsRequest;
import com.openhis.health.application.peis.registration.request.CreateRegistrationRequest;
import com.openhis.health.application.peis.registration.response.CheckOrderDetailResponse;
import com.openhis.health.application.peis.registration.response.CreateRegistrationResponse;
import com.openhis.health.application.peis.registration.response.SearchCustomerResponse;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.servicerequestext.queries.model.PeisServiceRequestExtModel;

import java.util.List;
import java.util.Map;

/**
 * 个检登记 DTO 映射（AS-PEIS-REG-001）。
 */
public interface PeisRegistrationMapper {

    CustomerRowDto toCustomerRowDto(PatientPageItemQueryModel source);

    SearchCustomerResponse toSearchCustomerResponse(List<CustomerRowDto> rows, long total);

    RegisterPersonalCheckInCommand toRegisterCommand(CreateRegistrationRequest request, Long operatorId);

    CreateRegistrationResponse toCreateRegistrationResponse(RegisterPersonalCheckInResult result);

    AdjustPeServiceRequestsCommand toAdjustCommand(AdjustServiceRequestsRequest request, Long operatorId);

    CheckOrderDetailResponse toCheckOrderDetailResponse(
        PeisEncounterExtModel ext,
        Long patientId,
        List<PeisServiceRequestExtModel> lines,
        Map<Long, String> activityDisplayById);

    ServiceRequestLineDto toServiceRequestLineDto(
        PeisServiceRequestExtModel source, String display);
}
