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

package com.openhis.health.application.emergency.doctorstation.encounter.service;

import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.emergency.doctorstation.encounter.request.PageEmergencyEncountersForDoctorStationRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.dto.OutpatientInfoItemDto;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.PageOutpatientInfoForDoctorStationResponse;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.EmergencyEncounterDoctorListQuery;
import com.openhis.health.application.querysupport.encounter.EmergencyEncounterListLoader;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.repository.OrganizationRepository;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 急诊医生站就诊列表：直接查 {@code adm_encounter} 主数据（{@link EncounterClass#EMER}），不经门诊信息读模型。
 */
@Service
@RequiredArgsConstructor
public class EmergencyDoctorStationEncounterAppService {

    private final EmergencyEncounterListLoader emergencyEncounterListLoader;
    private final EncounterQueryService encounterQueryService;
    private final PatientRepository patientRepository;
    private final OrganizationRepository organizationRepository;
    private final ICurrentUser currentUser;

    /**
     * 分页查询急诊就诊主数据列表（供 whale-emis 医生站待诊队列等）。
     *
     * @param request 分页与时段、关键字、状态列表
     * @return 与门诊医生站就诊列表行 DTO 同构的 {@link OutpatientInfoItemDto} 分页（费用性质下拉为空列表）
     */
    public PageOutpatientInfoForDoctorStationResponse pageEmergencyEncountersForDoctorStation(
        PageEmergencyEncountersForDoctorStationRequest request) {
        this.validateRequest(request);
        Long sessionOrganizationUnitId = this.currentUser.getCurrentOrganizationUnitId();
        List<String> statusIn = request.getEncounterStatusIn();
        if (statusIn == null || statusIn.isEmpty()) {
            statusIn = List.of(EncounterStatus.REGISTERED.getCode(), EncounterStatus.TRIAGED.getCode());
        } else {
            statusIn = statusIn.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
            if (statusIn.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊状态列表不能为空");
            }
        }
        EmergencyEncounterDoctorListQuery q = new EmergencyEncounterDoctorListQuery();
        q.setPageNo(request.getPageNo());
        q.setPageSize(request.getPageSize());
        q.setOrganizationId(sessionOrganizationUnitId);
        q.setStartTimeFrom(request.getStartDate());
        q.setStartTimeTo(request.getEndDate());
        q.setStatusEnumIn(statusIn);
        q.setKeyword(request.getKeyword());
        Page<Encounter> page = this.emergencyEncounterListLoader.pageEmergencyEncountersForDoctorStation(q);
        Set<Long> patientIds = new LinkedHashSet<>();
        Set<Long> orgIds = new LinkedHashSet<>();
        for (Encounter row : page.getContent()) {
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
            if (row.getOrganizationId() != null) {
                orgIds.add(row.getOrganizationId());
            }
        }
        Map<Long, Patient> patientById = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (Patient p : this.patientRepository.findAllByIdIn(patientIds)) {
                if (p.getId() != null) {
                    patientById.put(p.getId(), p);
                }
            }
        }
        Map<Long, String> orgNameById = new HashMap<>();
        if (!orgIds.isEmpty()) {
            for (Organization o : this.organizationRepository.findAllByIdIn(orgIds)) {
                if (o.getId() != null) {
                    orgNameById.put(o.getId(), o.getName());
                }
            }
        }
        List<OutpatientInfoItemDto> records = new ArrayList<>();
        for (Encounter enc : page.getContent()) {
            Patient patient = enc.getPatientId() == null ? null : patientById.get(enc.getPatientId());
            String orgName = enc.getOrganizationId() == null ? null : orgNameById.get(enc.getOrganizationId());
            records.add(this.toListRow(enc, patient, orgName));
        }
        PageOutpatientInfoForDoctorStationResponse resp =
                PagedResultBuilder.build(new PageOutpatientInfoForDoctorStationResponse(), records, page);
        resp.setFeeNatureOptions(Collections.emptyList());
        return resp;
    }

    private void validateRequest(PageEmergencyEncountersForDoctorStationRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "页码须从 1 开始");
        }
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "页大小须为正整数");
        }
    }

    private OutpatientInfoItemDto toListRow(Encounter enc, Patient patient, String organizationName) {
        OutpatientInfoItemDto dto = new OutpatientInfoItemDto();
        dto.setEncounterId(enc.getId());
        dto.setEncounterBusNo(enc.getBusNo());
        dto.setClassEnum(EnumUtil.toDisplayIntegerFromCodeOrNumber(EncounterClass.class, enc.getClassEnum()));
        dto.setStatusEnum(enc.getStatusEnum());
        EncounterStatus st = EnumUtil.fromCode(EncounterStatus.class, enc.getStatusEnum());
        dto.setStatusEnumText(st != null ? st.getDisplay() : enc.getStatusEnum());
        if (patient != null) {
            dto.setPatientId(patient.getId());
            dto.setPatientName(patient.getName());
            dto.setGenderEnum(patient.getGenderEnum());
            AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, patient.getGenderEnum());
            if (gender != null) {
                dto.setGenderEnumText(gender.getDisplay());
            }
            LocalDate birthDate = patient.getBirthDate();
            if (birthDate != null) {
                dto.setBirthDate(birthDate.atStartOfDay());
            }
            int years = AgeCalculatorUtil.calculateAge(birthDate);
            if (years > 0) {
                dto.setAgeText(years + "岁");
            }
            dto.setIdCard(patient.getIdCard());
            dto.setPhone(patient.getPhone());
        } else {
            dto.setPatientName("");
        }
        dto.setOrganizationId(enc.getOrganizationId());
        dto.setOrganizationName(organizationName);
        dto.setServiceTypeId(enc.getServiceTypeId());
        dto.setServiceTypeName(EncounterClass.EMER.getDisplay());
        dto.setStartTime(enc.getStartTime());
        dto.setReceptionTime(enc.getReceptionTime());
        dto.setEndTime(enc.getEndTime());
        dto.setDisplayOrder(enc.getDisplayOrder());
        return dto;
    }
}
