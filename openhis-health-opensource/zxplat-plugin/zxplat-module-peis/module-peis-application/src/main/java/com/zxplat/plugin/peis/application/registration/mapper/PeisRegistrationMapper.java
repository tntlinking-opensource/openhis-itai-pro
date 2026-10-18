/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.mapper;

import com.zxplat.plugin.peis.application.orchestration.command.AdjustPeServiceRequestsCommand;
import com.zxplat.plugin.peis.application.orchestration.command.RegisterPersonalCheckInCommand;
import com.zxplat.plugin.peis.application.orchestration.result.RegisterPersonalCheckInResult;
import com.zxplat.plugin.peis.application.registration.dto.CustomerRowDto;
import com.zxplat.plugin.peis.application.registration.dto.ServiceRequestLineDto;
import com.zxplat.plugin.peis.application.registration.request.AdjustServiceRequestsRequest;
import com.zxplat.plugin.peis.application.registration.request.CreateRegistrationRequest;
import com.zxplat.plugin.peis.application.registration.response.CheckOrderDetailResponse;
import com.zxplat.plugin.peis.application.registration.response.CreateRegistrationResponse;
import com.zxplat.plugin.peis.application.registration.response.SearchCustomerResponse;
import com.zxplat.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.plugin.peis.domain.servicerequestext.queries.model.PeisServiceRequestExtModel;

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
