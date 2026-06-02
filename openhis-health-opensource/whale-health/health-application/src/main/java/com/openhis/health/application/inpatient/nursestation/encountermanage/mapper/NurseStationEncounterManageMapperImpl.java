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

package com.openhis.health.application.inpatient.nursestation.encountermanage.mapper;

import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.*;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.PagePatientQueueRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.SavePatientInfoRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.LoadWardTreeResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.PagePatientQueueResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.SavePatientInfoResponse;
import com.openhis.health.application.common.assembler.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.domain.administration.encounter.command.SaveNurseStationPatientCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link NurseStationEncounterManageMapper} 实现：字段映射与展示回落，不承载业务流程判断。
 */
@Component
public class NurseStationEncounterManageMapperImpl implements NurseStationEncounterManageMapper {

    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;

    public NurseStationEncounterManageMapperImpl(
        AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler) {
        this.admissionPrimaryDiagnosisAssembler = admissionPrimaryDiagnosisAssembler;
    }

    /**
     * 病区与病房列表 → 树响应。
     *
     * @param wards           病区位置列表；调用方保证非 null
     * @param housesByWardId  病区 id → 子病房列表
     * @return 树响应
     */
    @Override
    public LoadWardTreeResponse toLoadWardTreeResponse(List<Location> wards, Map<Long, List<Location>> housesByWardId) {
        LoadWardTreeResponse response = new LoadWardTreeResponse();
        if (wards == null || wards.isEmpty()) {
            response.setWardNodes(Collections.emptyList());
            return response;
        }
        List<NurseStationWardTreeNodeDto> wardNodes = new ArrayList<>();
        for (Location ward : wards) {
            if (ward == null) {
                continue;
            }
            NurseStationWardTreeNodeDto wardNode = new NurseStationWardTreeNodeDto();
            wardNode.setLocationId(ward.getId());
            wardNode.setName(ward.getName());
            wardNode.setBusNo(ward.getBusNo());
            wardNode.setDisplayOrder(ward.getDisplayOrder());
            List<NurseStationHouseTreeNodeDto> houseNodes = new ArrayList<>();
            List<Location> houses = null;
            if (housesByWardId != null && ward.getId() != null) {
                houses = housesByWardId.get(ward.getId());
            }
            if (houses != null) {
                for (Location house : houses) {
                    if (house == null) {
                        continue;
                    }
                    NurseStationHouseTreeNodeDto houseNode = new NurseStationHouseTreeNodeDto();
                    houseNode.setLocationId(house.getId());
                    houseNode.setName(house.getName());
                    houseNode.setBusNo(house.getBusNo());
                    houseNode.setDisplayOrder(house.getDisplayOrder());
                    houseNodes.add(houseNode);
                }
            }
            wardNode.setHouseNodes(houseNodes);
            wardNodes.add(wardNode);
        }
        response.setWardNodes(wardNodes);
        return response;
    }

    /**
     * 列表分页请求 → 住院信息读模型分页入参。
     *
     * @param request          HTTP 请求
     * @param managedWardIds   管理病区 id 列表
     * @param encounterIdsIn   任务筛选预收窄就诊集合；可 null
     * @return 读模型分页查询
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(
        PagePatientQueueRequest request,
        List<Long> managedWardIds,
        Collection<Long> encounterIdsIn) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        if (request == null) {
            return query;
        }
        query.setWardId(request.getWardId());
        query.setHouseId(request.getHouseId());
        query.setNurseStationTaskFilter(request.getNurseStationTaskFilter());
        query.setNurseStationExecutionDate(request.getNurseStationExecutionDate());
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        if (encounterIdsIn != null && !encounterIdsIn.isEmpty()) {
            query.setEncounterIdsIn(encounterIdsIn);
        }
        return query;
    }

    /**
     * 读模型行 + 账户与任务计数 → 列表卡片 DTO。
     *
     * @param row               住院信息读模型
     * @param counts            任务计数；可 null 时按 0
     * @param accountBalance    展示余额
     * @param lowBalanceWarning 低余额预警
     * @param enrichment        展示增补；可 null
     * @param displayOverlay    队列展示覆盖；可 null
     * @param priorityEnum      病情/优先级 code；可 null
     * @param accountId         就诊默认个人现金账户主键；未开立可 null
     * @return 列表行 DTO；row 为 null 时返回 null
     */
    @Override
    public PatientQueueItemDto toPatientQueueItemDto(
        InpatientEncounterContextQueryModel row,
        NurseStationTaskCountBundle counts,
        BigDecimal accountBalance,
        boolean lowBalanceWarning,
        NurseStationQueueDisplayEnrichment enrichment,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
        String priorityEnum,
        Long accountId) {
        if (row == null) {
            return null;
        }
        String overlayBusNo = null;
        String overlayPatientName = null;
        String overlayOrgName = null;
        String overlayWardName = null;
        String overlayBedName = null;
        String overlayStatusText = null;
        if (displayOverlay != null) {
            overlayBusNo = displayOverlay.getEncounterBusNo();
            overlayPatientName = displayOverlay.getPatientName();
            overlayOrgName = displayOverlay.getOrganizationName();
            overlayWardName = displayOverlay.getWardName();
            overlayBedName = displayOverlay.getBedName();
            overlayStatusText = displayOverlay.getStatusEnumText();
        }
        String enrichmentHouseName = null;
        String enrichmentPinyin = null;
        String enrichmentWubi = null;
        if (enrichment != null) {
            enrichmentHouseName = enrichment.getHouseName();
            enrichmentPinyin = enrichment.getPinyinCode();
            enrichmentWubi = enrichment.getWubiCode();
        }
        PatientQueueItemDto dto = new PatientQueueItemDto();
        dto.setEncounterId(row.getEncounterId());
        dto.setAccountId(accountId);
        dto.setEncounterBusNo(this.firstNonBlank(row.getEncounterBusNo(), overlayBusNo));
        dto.setStatusEnum(row.getStatusEnum());
        dto.setStatusEnumText(this.firstNonBlank(row.getStatusEnumText(), overlayStatusText));
        dto.setPatientId(row.getPatientId());
        dto.setPatientName(this.firstNonBlank(row.getPatientName(), overlayPatientName));
        dto.setGenderEnum(row.getGenderEnum());
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, row.getGenderEnum());
        if (gender != null) {
            dto.setGenderEnumText(gender.getDisplay());
        }
        dto.setAgeText(row.getAgeText());
        dto.setWardId(row.getWardId());
        dto.setWardName(this.firstNonBlank(row.getWardName(), overlayWardName));
        dto.setHouseName(enrichmentHouseName);
        dto.setBedId(row.getBedId());
        dto.setBedName(this.firstNonBlank(row.getBedName(), overlayBedName));
        dto.setOrganizationId(row.getOrganizationId());
        dto.setOrganizationName(this.firstNonBlank(row.getOrganizationName(), overlayOrgName));
        dto.setPriorityEnum(priorityEnum);
        dto.setStartTime(row.getStartTime());
        // 入科时间：InpatientInfo 读模型当前未投影独立列，与 SDD §3.3 约定一致返回 null。
        dto.setAdmissionTime(null);
        dto.setTotalAmount(row.getTotalAmount());
        dto.setPaidAmount(row.getPaidAmount());
        dto.setArrearsAmount(row.getArrearsAmount());
        dto.setAccountBalance(accountBalance);
        dto.setLowBalanceWarning(lowBalanceWarning);
        int pendingAudit = 0;
        int pendingExecute = 0;
        if (counts != null) {
            if (counts.getPendingAuditCount() != null) {
                pendingAudit = counts.getPendingAuditCount();
            }
            if (counts.getPendingExecuteCount() != null) {
                pendingExecute = counts.getPendingExecuteCount();
            }
        }
        dto.setPendingAuditCount(pendingAudit);
        dto.setPendingExecuteCount(pendingExecute);
        dto.setAttendingDoctorName(row.getAttendingDoctorName());
        dto.setPrimaryNurseName(row.getPrimaryNurseName());
        List<String> diagnosisLines =
            admissionPrimaryDiagnosisAssembler.resolveDiagnosisDisplayLines(row.getDiagnosisJson());
        dto.setDiagnosisList(diagnosisLines);
        dto.setDiagnosisSummary(
            admissionPrimaryDiagnosisAssembler.joinDiagnosisDisplayLines(diagnosisLines));
        dto.setPinyinCode(enrichmentPinyin);
        dto.setWubiCode(enrichmentWubi);
        return dto;
    }

    /**
     * 已装配列表行 + Spring 分页 → 分页响应。
     *
     * @param rows 当前页 DTO
     * @param page 读模型分页
     * @return 分页响应
     */
    @Override
    public PagePatientQueueResponse toPagePatientQueueResponse(
        List<PatientQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page) {
        return PagedResultBuilder.build(new PagePatientQueueResponse(), rows, page);
    }

    /**
     * 列表行 + 详情扩展 → 全景详情 DTO。
     *
     * @param base          列表同源字段
     * @param patientPhone  电话
     * @param idCardNo      证件号（已脱敏）
     * @param participants  全角色参与者
     * @return 详情 DTO
     */
    @Override
    public PatientDetailDto toPatientDetailDto(
        PatientQueueItemDto base,
        String patientPhone,
        String idCardNo,
        List<ParticipantDto> participants) {
        if (base == null) {
            return null;
        }
        PatientDetailDto detail = new PatientDetailDto();
        detail.setEncounterId(base.getEncounterId());
        detail.setEncounterBusNo(base.getEncounterBusNo());
        detail.setStatusEnum(base.getStatusEnum());
        detail.setStatusEnumText(base.getStatusEnumText());
        detail.setPatientId(base.getPatientId());
        detail.setPatientName(base.getPatientName());
        detail.setGenderEnum(base.getGenderEnum());
        detail.setGenderEnumText(base.getGenderEnumText());
        detail.setAgeText(base.getAgeText());
        detail.setWardId(base.getWardId());
        detail.setWardName(base.getWardName());
        detail.setHouseName(base.getHouseName());
        detail.setBedId(base.getBedId());
        detail.setBedName(base.getBedName());
        detail.setOrganizationId(base.getOrganizationId());
        detail.setOrganizationName(base.getOrganizationName());
        detail.setPriorityEnum(base.getPriorityEnum());
        detail.setStartTime(base.getStartTime());
        detail.setAdmissionTime(base.getAdmissionTime());
        detail.setTotalAmount(base.getTotalAmount());
        detail.setPaidAmount(base.getPaidAmount());
        detail.setArrearsAmount(base.getArrearsAmount());
        detail.setAccountBalance(base.getAccountBalance());
        detail.setLowBalanceWarning(base.getLowBalanceWarning());
        detail.setPendingAuditCount(base.getPendingAuditCount());
        detail.setPendingExecuteCount(base.getPendingExecuteCount());
        detail.setAttendingDoctorName(base.getAttendingDoctorName());
        detail.setPrimaryNurseName(base.getPrimaryNurseName());
        detail.setDiagnosisSummary(base.getDiagnosisSummary());
        detail.setDiagnosisList(base.getDiagnosisList());
        detail.setPinyinCode(base.getPinyinCode());
        detail.setWubiCode(base.getWubiCode());
        detail.setPatientPhone(patientPhone);
        detail.setIdCardNo(idCardNo);
        detail.setParticipants(participants);
        return detail;
    }

    /**
     * 维护请求 + 操作人 → 领域保存命令。
     *
     * @param request    维护请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public SaveNurseStationPatientCommand toSaveNurseStationPatientCommand(
        SavePatientInfoRequest request,
        Long operatorId) {
        if (request == null) {
            return null;
        }
        SaveNurseStationPatientCommand command = new SaveNurseStationPatientCommand();
        command.setOperatorId(operatorId);
        SaveNurseStationPatientCommand.ItemCommand item = new SaveNurseStationPatientCommand.ItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setPriorityEnum(request.getPriorityEnum());
        List<SaveNurseStationPatientCommand.ParticipantLine> lines = new ArrayList<>();
        if (request.getParticipants() != null) {
            for (ParticipantSubmitDto row : request.getParticipants()) {
                if (row == null) {
                    continue;
                }
                SaveNurseStationPatientCommand.ParticipantLine line = new SaveNurseStationPatientCommand.ParticipantLine();
                line.setRoleCode(row.getRoleCode());
                line.setPractitionerId(row.getPractitionerId());
                lines.add(line);
            }
        }
        item.setParticipants(lines);
        command.setItem(item);
        return command;
    }

    /**
     * 就诊主键 → 维护成功响应。
     *
     * @param encounterId 就诊主键
     * @return 维护响应
     */
    @Override
    public SavePatientInfoResponse toSavePatientInfoResponse(Long encounterId) {
        SavePatientInfoResponse response = new SavePatientInfoResponse();
        response.setEncounterId(encounterId);
        response.setSaved(Boolean.TRUE);
        return response;
    }

    /**
     * 读模型原值优先，空白时回落展示覆盖。
     *
     * @param fromRow     读模型字段
     * @param fromOverlay 覆盖回填
     * @return 展示字符串或 null
     */
    private String firstNonBlank(String fromRow, String fromOverlay) {
        if (fromRow != null && !fromRow.isBlank()) {
            return fromRow;
        }
        if (fromOverlay != null && !fromOverlay.isBlank()) {
            return fromOverlay;
        }
        return null;
    }
}
