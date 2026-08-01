/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstaff.mapper;

import com.openhis.campus.application.campusstaff.request.CreateStaffRequest;
import com.openhis.campus.application.campusstaff.request.UpdateStaffRequest;
import com.openhis.campus.application.campusstaff.response.CampusStaffDto;
import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import org.springframework.stereotype.Component;

@Component
public class CampusStaffMapper {

    public CampusStaffDto toDto(PatientStaff entity) {
        CampusStaffDto dto = new CampusStaffDto();
        dto.setId(entity.getId());
        dto.setPatientId(entity.getPatientId());
        dto.setName(entity.getName());
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        dto.setGenderEnum(entity.getGenderEnum());
        dto.setIdCard(entity.getIdCard());
        dto.setPhone(entity.getPhone());
        dto.setEmployeeNo(entity.getEmployeeNo());
        dto.setMedicalCode(entity.getMedicalCode());
        dto.setDepartmentId(entity.getDepartmentId());
        dto.setCampusSiteId(entity.getCampusSiteId());
        dto.setStaffTypeEnum(entity.getStaffTypeEnum());
        dto.setValidFrom(entity.getValidFrom());
        dto.setValidTo(entity.getValidTo());
        dto.setJobStatusEnum(entity.getJobStatusEnum());
        dto.setActiveFlag(entity.getActiveFlag());
        return dto;
    }

    public PatientStaff toEntity(CreateStaffRequest request) {
        PatientStaff entity = new PatientStaff();
        entity.setTenantId(request.getTenantId());
        entity.setName(request.getName());
        entity.setPyStr(request.getPyStr());
        entity.setWbStr(request.getWbStr());
        entity.setGenderEnum(request.getGenderEnum());
        entity.setIdCard(request.getIdCard());
        entity.setPhone(request.getPhone());
        entity.setEmployeeNo(request.getEmployeeNo());
        entity.setMedicalCode(request.getMedicalCode());
        entity.setDepartmentId(request.getDepartmentId());
        entity.setCampusSiteId(request.getCampusSiteId());
        entity.setStaffTypeEnum(request.getStaffTypeEnum());
        entity.setValidFrom(request.getValidFrom());
        entity.setValidTo(request.getValidTo());
        entity.setJobStatusEnum(request.getJobStatusEnum());
        entity.setActiveFlag(request.getActiveFlag());
        entity.setPatientId(request.getPatientId());
        return entity;
    }

    public PatientStaff toEntity(UpdateStaffRequest request) {
        PatientStaff entity = new PatientStaff();
        entity.setTenantId(request.getTenantId());
        entity.setId(request.getId());
        entity.setName(request.getName());
        entity.setPyStr(request.getPyStr());
        entity.setWbStr(request.getWbStr());
        entity.setGenderEnum(request.getGenderEnum());
        entity.setIdCard(request.getIdCard());
        entity.setPhone(request.getPhone());
        entity.setEmployeeNo(request.getEmployeeNo());
        entity.setMedicalCode(request.getMedicalCode());
        entity.setDepartmentId(request.getDepartmentId());
        entity.setCampusSiteId(request.getCampusSiteId());
        entity.setStaffTypeEnum(request.getStaffTypeEnum());
        entity.setValidFrom(request.getValidFrom());
        entity.setValidTo(request.getValidTo());
        entity.setJobStatusEnum(request.getJobStatusEnum());
        entity.setActiveFlag(request.getActiveFlag());
        return entity;
    }
}
