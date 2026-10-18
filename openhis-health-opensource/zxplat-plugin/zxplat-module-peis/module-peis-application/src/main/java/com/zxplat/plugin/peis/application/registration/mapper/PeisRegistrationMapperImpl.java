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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link PeisRegistrationMapper} 默认实现（AS-PEIS-REG-001）。
 */
@Component
public class PeisRegistrationMapperImpl implements PeisRegistrationMapper {

    @Override
    public CustomerRowDto toCustomerRowDto(PatientPageItemQueryModel source) {
        if (source == null) {
            return null;
        }
        CustomerRowDto dto = new CustomerRowDto();
        dto.setPatientId(source.getId());
        dto.setName(source.getName());
        dto.setIdCardNo(source.getIdCard());
        dto.setGenderCode(source.getGenderEnum());
        dto.setPhone(source.getPhone());
        return dto;
    }

    @Override
    public SearchCustomerResponse toSearchCustomerResponse(List<CustomerRowDto> rows, long total) {
        SearchCustomerResponse response = new SearchCustomerResponse();
        if (rows == null) {
            response.setRows(Collections.emptyList());
        } else {
            response.setRows(rows);
        }
        response.setTotal(total);
        return response;
    }

    @Override
    public RegisterPersonalCheckInCommand toRegisterCommand(
            CreateRegistrationRequest request, Long operatorId) {
        RegisterPersonalCheckInCommand command = new RegisterPersonalCheckInCommand();
        command.setPatientId(request.getPatientId());
        command.setPackageActivityId(request.getPackageActivityId());
        command.setOrgId(request.getOrgId());
        command.setBookingAppointmentId(request.getBookingAppointmentId());
        command.setOperatorId(operatorId);
        command.setExamKind(request.getExamKind());
        command.setCompanyId(request.getCompanyId());
        command.setGroupBatchId(request.getGroupBatchId());
        command.setReferrerName(request.getReferrerName());
        command.setBreakfastFlag(request.getBreakfastFlag());
        return command;
    }

    @Override
    public CreateRegistrationResponse toCreateRegistrationResponse(RegisterPersonalCheckInResult result) {
        CreateRegistrationResponse response = new CreateRegistrationResponse();
        if (result == null) {
            return response;
        }
        response.setEncounterId(result.getEncounterId());
        response.setBusNo(result.getBusNo());
        response.setServiceRequestCount(result.getServiceRequestCount());
        return response;
    }

    @Override
    public AdjustPeServiceRequestsCommand toAdjustCommand(
            AdjustServiceRequestsRequest request, Long operatorId) {
        AdjustPeServiceRequestsCommand command = new AdjustPeServiceRequestsCommand();
        command.setEncounterId(request.getEncounterId());
        command.setAddActivityIds(request.getAddActivityIds());
        command.setRemoveServiceRequestIds(request.getRemoveServiceRequestIds());
        command.setOperatorId(operatorId);
        return command;
    }

    @Override
    public CheckOrderDetailResponse toCheckOrderDetailResponse(
            PeisEncounterExtModel ext,
            Long patientId,
            List<PeisServiceRequestExtModel> lines,
            Map<Long, String> activityDisplayById) {
        CheckOrderDetailResponse response = new CheckOrderDetailResponse();
        if (ext != null) {
            response.setEncounterId(ext.getEncounterId());
            response.setBusNo(ext.getBusNo());
            response.setPackageActivityId(ext.getPackageActivityId());
        }
        response.setPatientId(patientId);
        List<ServiceRequestLineDto> dtoLines = new ArrayList<>();
        if (lines != null) {
            for (PeisServiceRequestExtModel line : lines) {
                if (line == null) {
                    continue;
                }
                String display = null;
                if (activityDisplayById != null && line.getActivityId() != null) {
                    display = activityDisplayById.get(line.getActivityId());
                }
                dtoLines.add(toServiceRequestLineDto(line, display));
            }
        }
        response.setServiceRequestLines(dtoLines);
        return response;
    }

    @Override
    public ServiceRequestLineDto toServiceRequestLineDto(
            PeisServiceRequestExtModel source, String display) {
        ServiceRequestLineDto dto = new ServiceRequestLineDto();
        if (source == null) {
            return dto;
        }
        dto.setServiceRequestId(source.getServiceRequestId());
        dto.setActivityId(source.getActivityId());
        dto.setDisplay(display);
        dto.setExecStatusCode(source.getExecStatusEnum());
        return dto;
    }
}
