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

package com.openhis.health.application.emergency.emgtriage.station.mapper;

import com.openhis.health.application.emergency.emgtriage.station.dto.EmgTriageExtendedJsonFieldOptionsDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.EmgTriageWorkbenchRowDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.KnowledgeCandidateRowDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.KnowledgeSearchPresetDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.PatientIdentifierItemDto;
import com.openhis.health.application.emergency.emgtriage.station.dto.SaveEmgTriageAssessmentItemDto;
import com.openhis.health.application.emergency.emgtriage.station.request.AmendEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.PageEmgTriageWorkbenchRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.RegisterOrUpdatePatientForEmergencyRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SaveOrSubmitEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SearchPatientsForEmergencyTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SubmitSecondaryEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.VoidEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.response.InitializeEmergencyTriageStationResponse;
import com.openhis.health.domain.administration.patient.command.PatientIdentifierItemCommand;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.emergency.emgtriage.command.AmendEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.command.SaveEmgTriageAssessmentItemCommand;
import com.openhis.health.domain.emergency.emgtriage.command.SaveEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.command.VoidEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriage;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriageAssessment;
import com.openhis.health.domain.emergency.emgtriage.queries.EmgTriagePageQuery;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageByEncounterQueryModel;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageListItemQueryModel;
import com.openhis.health.domain.emergency.knowledge.queries.model.KnowledgeBaseItemQueryModel;
import com.openhis.health.domain.share.enums.IdentifierState;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link EmergencyTriageStationMapper} 的实现：字段搬运与少量枚举解析。
 */
@Component
public class EmergencyTriageStationMapperImpl implements EmergencyTriageStationMapper {

    /**
     * IF-EMG-06：保存分诊请求 → 领域命令。
     *
     * @param request    请求体
     * @param operatorId 操作人（来自登录上下文，不由客户端传）
     * @return 领域命令
     */
    @Override
    public SaveEmgTriageCommand toSaveEmgTriageCommand(SaveOrSubmitEmgTriageRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        SaveEmgTriageCommand cmd = new SaveEmgTriageCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(request.getId());
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(request.getPatientId());
        cmd.setTriageTime(request.getTriageTime());
        cmd.setChiefComplaintJson(request.getChiefComplaintJson());
        cmd.setSymptomJson(request.getSymptomJson());
        cmd.setTriageLevelEnum(request.getTriageLevelEnum());
        cmd.setSuggestedLevelEnum(request.getSuggestedLevelEnum());
        cmd.setLevelAdjustReason(request.getLevelAdjustReason());
        cmd.setChangeReasonCode(request.getChangeReasonCode());
        cmd.setChangeRemark(request.getChangeRemark());
        cmd.setOrganizationId(request.getOrganizationId());
        cmd.setLocationId(request.getLocationId());
        cmd.setAssignedDoctorSnapshot(request.getAssignedDoctorSnapshot());
        cmd.setDiseaseTypesJson(request.getDiseaseTypesJson());
        cmd.setPatientSource(request.getPatientSource());
        cmd.setIsGreenChannel(request.getIsGreenChannel());
        cmd.setIsFreeRegister(request.getIsFreeRegister());
        cmd.setQueuePriority(request.getQueuePriority());
        cmd.setNote(request.getNote());
        cmd.setTriageRegistrationModeEnum(request.getTriageRegistrationModeEnum());
        cmd.setIsStaged(request.getIsStaged());
        cmd.setStageQueueSeq(request.getStageQueueSeq());
        cmd.setProvisionalTriageNo(request.getProvisionalTriageNo());
        cmd.setSpecialPopulationTagsJson(request.getSpecialPopulationTagsJson());
        cmd.setMciEventId(request.getMciEventId());
        cmd.setBlacklistAlertAck(request.getBlacklistAlertAck());
        cmd.setArrivalModeEnum(request.getArrivalModeEnum());
        cmd.setTransferInOrgId(request.getTransferInOrgId());
        cmd.setDestinationModeEnum(request.getDestinationModeEnum());
        cmd.setSecondaryLocationId(request.getSecondaryLocationId());
        cmd.setGreenChannelOnsetTime(request.getGreenChannelOnsetTime());
        cmd.setEpidemicSurveyJson(request.getEpidemicSurveyJson());
        cmd.setSpecialtyGreenChannelCode(request.getSpecialtyGreenChannelCode());
        cmd.setAmbulance120Json(request.getAmbulance120Json());
        cmd.setPolice110Json(request.getPolice110Json());
        cmd.setMonitorDeviceBindingJson(request.getMonitorDeviceBindingJson());
        cmd.setWristbandPrintedAt(request.getWristbandPrintedAt());
        cmd.setVitalsAutoTierPolicyId(request.getVitalsAutoTierPolicyId());
        cmd.setChiefComplaintOptional(request.getChiefComplaintOptional());
        cmd.setPriorEmgTriageId(null);
        cmd.setAssessmentItems(mapAssessmentItems(request.getAssessmentItems()));
        return cmd;
    }

    /**
     * IF-EMG-09：二次分诊请求 → 领域命令。
     *
     * @param request    请求体
     * @param operatorId 操作人
     * @return 领域命令（含 prior）
     */
    @Override
    public SaveEmgTriageCommand toSaveEmgTriageCommand(SubmitSecondaryEmgTriageRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        SaveEmgTriageCommand cmd = this.toSaveEmgTriageCommand((SaveOrSubmitEmgTriageRequest) request, operatorId);
        cmd.setPriorEmgTriageId(request.getPriorEmgTriageId());
        cmd.setId(null);
        return cmd;
    }

    /**
     * IF-EMG-07：修订分诊请求 → 领域命令。
     *
     * @param request    请求体
     * @param operatorId 操作人
     * @return 修订命令
     */
    @Override
    public AmendEmgTriageCommand toAmendEmgTriageCommand(AmendEmgTriageRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        AmendEmgTriageCommand cmd = new AmendEmgTriageCommand();
        cmd.setEmgTriageId(request.getEmgTriageId());
        cmd.setOperatorId(operatorId);
        cmd.setTriageLevelEnum(request.getTriageLevelEnum());
        cmd.setSuggestedLevelEnum(request.getSuggestedLevelEnum());
        cmd.setLevelAdjustReason(request.getLevelAdjustReason());
        cmd.setOrganizationId(request.getOrganizationId());
        cmd.setLocationId(request.getLocationId());
        cmd.setSecondaryLocationId(request.getSecondaryLocationId());
        cmd.setDestinationModeEnum(request.getDestinationModeEnum());
        cmd.setQueuePriority(request.getQueuePriority());
        cmd.setNote(request.getNote());
        cmd.setIsGreenChannel(request.getIsGreenChannel());
        cmd.setIsFreeRegister(request.getIsFreeRegister());
        cmd.setGreenChannelOnsetTime(request.getGreenChannelOnsetTime());
        cmd.setChangeReasonCode(request.getChangeReasonCode());
        cmd.setChangeRemark(request.getChangeRemark());
        cmd.setOccurredAt(request.getOccurredAt());
        return cmd;
    }

    /**
     * IF-EMG-08：作废分诊请求 → 领域命令。
     *
     * @param request    请求体
     * @param operatorId 操作人
     * @return 作废命令
     */
    @Override
    public VoidEmgTriageCommand toVoidEmgTriageCommand(VoidEmgTriageRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        VoidEmgTriageCommand cmd = new VoidEmgTriageCommand();
        cmd.setEmgTriageId(request.getEmgTriageId());
        cmd.setVoidReason(request.getVoidReason());
        cmd.setOperatorId(operatorId);
        return cmd;
    }

    /**
     * IF-EMG-02：检索请求 → 患者分页查询。
     *
     * @param request 请求体
     * @return 患者分页查询入参
     */
    @Override
    public PatientPagedQuery toPatientPagedQuery(SearchPatientsForEmergencyTriageRequest request) {
        if (request == null) {
            return null;
        }
        PatientPagedQuery q = new PatientPagedQuery();
        q.setOrganizationId(request.getOrganizationId());
        q.setName(request.getKeyword());
        q.setPageNo(request.getPageNo());
        q.setPageSize(request.getBoundedPageSize());
        return q;
    }

    /**
     * IF-EMG-03：建档请求 → 注册命令。
     *
     * @param request 请求体
     * @return 注册命令
     */
    @Override
    public RegisterPatientCommand toRegisterPatientCommand(RegisterOrUpdatePatientForEmergencyRequest request) {
        if (request == null) {
            return null;
        }
        RegisterPatientCommand cmd = new RegisterPatientCommand();
        cmd.setOccurredAt(request.getOccurredAt());
        RegisterPatientCommand.RegisterPatientItemCommand item = new RegisterPatientCommand.RegisterPatientItemCommand();
        item.setName(request.getName());
        item.setGenderEnum(request.getGenderEnum());
        item.setBirthDate(request.getBirthDate());
        item.setIdCard(request.getIdCard());
        item.setPhone(request.getPhone());
        item.setTempFlag(request.getTempFlag());
        item.setOrganizationId(request.getOrganizationId());
        item.setIdentifierItems(mapIdentifierItems(request.getIdentifierItems()));
        cmd.setItem(item);
        return cmd;
    }

    /**
     * IF-EMG-18：工作台分页查询入参映射。
     *
     * @param request 请求体
     * @return 分诊分页过滤条件
     */
    @Override
    public EmgTriagePageQuery toEmgTriagePageQuery(PageEmgTriageWorkbenchRequest request) {
        if (request == null) {
            return null;
        }
        EmgTriagePageQuery q = new EmgTriagePageQuery();
        q.setOrganizationId(request.getOrganizationId());
        q.setPatientNameHint(request.getPatientNameHint());
        q.setTriageLevelEnum(request.getTriageLevelEnum());
        q.setEncounterId(request.getEncounterId());
        q.setPatientId(request.getPatientId());
        q.setIncludeAllTriageStatuses(request.getIncludeAllTriageStatuses());
        return q;
    }

    /**
     * IF-EMG-18：列表行映射为工作台 DTO。
     *
     * @param row 查询模型行
     * @return 工作台 DTO
     */
    @Override
    public EmgTriageWorkbenchRowDto toWorkbenchRowDto(EmgTriageListItemQueryModel row) {
        if (row == null) {
            return null;
        }
        EmgTriageWorkbenchRowDto dto = new EmgTriageWorkbenchRowDto();
        dto.setEmgTriageId(row.getEmgTriageId());
        dto.setEncounterId(row.getEncounterId());
        dto.setPatientId(row.getPatientId());
        dto.setPatientName(row.getPatientName());
        dto.setTriageTime(row.getTriageTime());
        dto.setTriageLevelEnum(row.getTriageLevelEnum());
        dto.setSuggestedLevelEnum(row.getSuggestedLevelEnum());
        dto.setOrganizationId(row.getOrganizationId());
        dto.setOrganizationName(row.getOrganizationName());
        dto.setLocationId(row.getLocationId());
        dto.setSecondaryLocationId(row.getSecondaryLocationId());
        dto.setDestinationModeEnum(row.getDestinationModeEnum());
        dto.setQueuePriority(row.getQueuePriority());
        dto.setIsGreenChannel(row.getIsGreenChannel());
        dto.setIsStaged(row.getIsStaged());
        dto.setTriageStatusEnum(row.getTriageStatusEnum());
        return dto;
    }

    /**
     * 将 ACTIVE 分诊详情快照装配为 IF-EMG-06 同构请求体（工作站右侧编辑回显）。
     *
     * @param snap 分诊详情查询模型
     * @return 与保存接口一致的请求体
     */
    @Override
    public SaveOrSubmitEmgTriageRequest toSaveOrSubmitEmgTriageRequest(EmgTriageByEncounterQueryModel snap) {
        if (snap == null) {
            return null;
        }
        SaveOrSubmitEmgTriageRequest req = new SaveOrSubmitEmgTriageRequest();
        req.setId(snap.getId());
        req.setEncounterId(snap.getEncounterId());
        req.setPatientId(snap.getPatientId());
        LocalDateTime triageTime = snap.getTriageTime();
        if (triageTime == null) {
            triageTime = LocalDateTime.now();
        }
        req.setTriageTime(triageTime);
        req.setChiefComplaintJson(snap.getChiefComplaintJson());
        req.setSymptomJson(snap.getSymptomJson());
        Integer lvl = snap.getTriageLevelEnum();
        if (lvl == null) {
            lvl = 3;
        }
        req.setTriageLevelEnum(lvl);
        req.setSuggestedLevelEnum(snap.getSuggestedLevelEnum());
        req.setLevelAdjustReason(snap.getLevelAdjustReason());
        req.setChangeReasonCode(snap.getChangeReasonCode());
        req.setChangeRemark(snap.getChangeRemark());
        req.setOrganizationId(snap.getOrganizationId());
        Long locId = snap.getLocationId();
        if (locId == null) {
            locId = 0L;
        }
        req.setLocationId(locId);
        req.setAssignedDoctorSnapshot(snap.getAssignedDoctorSnapshot());
        req.setDiseaseTypesJson(snap.getDiseaseTypesJson());
        req.setPatientSource(snap.getPatientSource());
        req.setIsGreenChannel(Boolean.TRUE.equals(snap.getIsGreenChannel()));
        req.setIsFreeRegister(Boolean.TRUE.equals(snap.getIsFreeRegister()));
        Integer qp = snap.getQueuePriority();
        if (qp == null) {
            qp = 50;
        }
        req.setQueuePriority(qp);
        req.setNote(snap.getNote());
        req.setTriageRegistrationModeEnum(snap.getTriageRegistrationModeEnum());
        req.setIsStaged(Boolean.TRUE.equals(snap.getIsStaged()));
        req.setStageQueueSeq(snap.getStageQueueSeq());
        req.setProvisionalTriageNo(snap.getProvisionalTriageNo());
        req.setSpecialPopulationTagsJson(snap.getSpecialPopulationTagsJson());
        req.setMciEventId(snap.getMciEventId());
        req.setBlacklistAlertAck(Boolean.TRUE.equals(snap.getBlacklistAlertAck()));
        req.setArrivalModeEnum(snap.getArrivalModeEnum());
        req.setTransferInOrgId(snap.getTransferInOrgId());
        String destMode = snap.getDestinationModeEnum();
        if (StringUtils.isBlank(destMode)) {
            destMode = EmgTriage.DESTINATION_SINGLE;
        }
        req.setDestinationModeEnum(destMode);
        req.setSecondaryLocationId(snap.getSecondaryLocationId());
        req.setGreenChannelOnsetTime(snap.getGreenChannelOnsetTime());
        req.setEpidemicSurveyJson(snap.getEpidemicSurveyJson());
        req.setSpecialtyGreenChannelCode(snap.getSpecialtyGreenChannelCode());
        req.setAmbulance120Json(snap.getAmbulance120Json());
        req.setPolice110Json(snap.getPolice110Json());
        req.setMonitorDeviceBindingJson(snap.getMonitorDeviceBindingJson());
        req.setWristbandPrintedAt(snap.getWristbandPrintedAt());
        req.setVitalsAutoTierPolicyId(snap.getVitalsAutoTierPolicyId());
        req.setChiefComplaintOptional(Boolean.FALSE);
        req.setPriorEmgTriageId(snap.getPriorEmgTriageId());
        req.setAssessmentItems(toAssessmentItemDtos(snap.getAssessments()));
        return req;
    }

    private List<SaveEmgTriageAssessmentItemDto> toAssessmentItemDtos(List<EmgTriageAssessment> rows) {
        List<SaveEmgTriageAssessmentItemDto> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (EmgTriageAssessment e : rows) {
            if (e == null || Boolean.TRUE.equals(e.getIsDeleted())) {
                continue;
            }
            SaveEmgTriageAssessmentItemDto dto = new SaveEmgTriageAssessmentItemDto();
            dto.setId(e.getId());
            dto.setAssessmentType(e.getAssessmentType());
            dto.setAssessmentDataJson(e.getAssessmentData());
            dto.setAssessmentScore(e.getAssessmentScore());
            LocalDateTime assessedAt = e.getAssessedAt();
            if (assessedAt == null) {
                assessedAt = LocalDateTime.now();
            }
            dto.setAssessedAt(assessedAt);
            dto.setAssessedBy(e.getAssessedBy());
            list.add(dto);
        }
        return list;
    }

    /**
     * 知识库维护行映射。
     *
     * @param row 查询模型行
     * @return 维护行 DTO
     */
    @Override
    public com.openhis.health.application.emergency.emgtriage.station.dto.EmgKnowledgeBaseItemMaintenanceDto
            toEmgKnowledgeBaseItemMaintenanceDto(KnowledgeBaseItemQueryModel row) {
        if (row == null) {
            return null;
        }
        var dto = new com.openhis.health.application.emergency.emgtriage.station.dto.EmgKnowledgeBaseItemMaintenanceDto();
        dto.setId(row.getId());
        dto.setName(row.getName());
        dto.setPinyinCode(row.getPinyinCode());
        dto.setTriageLevel(row.getTriageLevel());
        dto.setType(row.getType());
        dto.setCategory(row.getCategory());
        dto.setSortOrder(row.getSortOrder());
        dto.setStatus(row.getStatus());
        return dto;
    }

    /**
     * IF-EMG-13：知识库分页行映射。
     *
     * @param row 查询模型行
     * @return 候选行 DTO
     */
    @Override
    public KnowledgeCandidateRowDto toKnowledgeCandidateRowDto(KnowledgeBaseItemQueryModel row) {
        if (row == null) {
            return null;
        }
        KnowledgeCandidateRowDto dto = new KnowledgeCandidateRowDto();
        dto.setId(row.getId());
        dto.setName(row.getName());
        dto.setPinyinCode(row.getPinyinCode());
        dto.setTriageLevel(row.getTriageLevel());
        dto.setType(row.getType());
        dto.setCategory(row.getCategory());
        return dto;
    }

    /**
     * IF-EMG-01：初始化响应装配。
     *
     * @param triageLevelOptions      分级下拉
     * @param departmentOptions       科室下拉
     * @param destinationOptions      去向下拉
     * @param preset                  知识预设
     * @param vitalsPolicyOptions           体征策略键
     * @param symptomDictVersion            字典版本戳
     * @param specialtyGreenChannelOptions  专病绿通编码选项
     * @param patientSourceOptions            患者来源选项
     * @param arrivalModeOptions              来院方式选项
     * @param transferInOrgOptions            转入机构选项
     * @param mciEventOptions                   群伤事件选项
     * @param chiefComplaintOptionalPolicyOptions 主诉是否可空策略
     * @param chiefComplaintTermOptions           主诉词条
     * @param symptomTermOptions                  症状词条
     * @param extendedJsonFieldOptions            扩展 JSON 字段多选项
     * @return 初始化响应
     */
    @Override
    public InitializeEmergencyTriageStationResponse toInitializeEmergencyTriageStationResponse(
            List<OptionDto> triageLevelOptions,
            List<OptionDto> departmentOptions,
            List<OptionDto> destinationOptions,
            KnowledgeSearchPresetDto preset,
            List<OptionDto> vitalsPolicyOptions,
            String symptomDictVersion,
            List<OptionDto> specialtyGreenChannelOptions,
            List<OptionDto> patientSourceOptions,
            List<OptionDto> arrivalModeOptions,
            List<OptionDto> transferInOrgOptions,
            List<OptionDto> mciEventOptions,
            List<OptionDto> chiefComplaintOptionalPolicyOptions,
            List<OptionDto> chiefComplaintTermOptions,
            List<OptionDto> symptomTermOptions,
            EmgTriageExtendedJsonFieldOptionsDto extendedJsonFieldOptions) {
        InitializeEmergencyTriageStationResponse resp = new InitializeEmergencyTriageStationResponse();
        resp.setTriageLevelOptions(triageLevelOptions);
        resp.setDepartmentOptions(departmentOptions);
        resp.setDestinationOptions(destinationOptions);
        resp.setKnowledgeSearchPreset(preset);
        resp.setVitalsPolicyOptions(vitalsPolicyOptions);
        resp.setSymptomDictVersion(symptomDictVersion);
        resp.setSpecialtyGreenChannelOptions(specialtyGreenChannelOptions);
        resp.setPatientSourceOptions(patientSourceOptions);
        resp.setArrivalModeOptions(arrivalModeOptions);
        resp.setTransferInOrgOptions(transferInOrgOptions);
        resp.setMciEventOptions(mciEventOptions);
        resp.setChiefComplaintOptionalPolicyOptions(chiefComplaintOptionalPolicyOptions);
        resp.setChiefComplaintTermOptions(chiefComplaintTermOptions);
        resp.setSymptomTermOptions(symptomTermOptions);
        EmgTriageExtendedJsonFieldOptionsDto extendedOptions = extendedJsonFieldOptions;
        if (extendedOptions == null) {
            extendedOptions = new EmgTriageExtendedJsonFieldOptionsDto();
        }
        resp.setExtendedJsonFieldOptions(extendedOptions);
        return resp;
    }

    /**
     * IF-EMG-14：腕带打印更新命令（基于当前 ACTIVE 分诊快照）。
     *
     * @param snapshot           就诊维度快照
     * @param operatorId         操作人
     * @param wristbandPrintedAt 打印时间
     * @return 保存命令
     */
    @Override
    public SaveEmgTriageCommand toSaveEmgTriageCommandForPrint(
            EmgTriageByEncounterQueryModel snapshot, Long operatorId, LocalDateTime wristbandPrintedAt) {
        if (snapshot == null) {
            return null;
        }
        SaveEmgTriageCommand cmd = new SaveEmgTriageCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(snapshot.getId());
        cmd.setEncounterId(snapshot.getEncounterId());
        cmd.setPatientId(snapshot.getPatientId());
        cmd.setTriageTime(snapshot.getTriageTime());
        cmd.setTriageStatusEnum(snapshot.getTriageStatusEnum());
        cmd.setChiefComplaintJson(snapshot.getChiefComplaintJson());
        cmd.setSymptomJson(snapshot.getSymptomJson());
        cmd.setTriageLevelEnum(snapshot.getTriageLevelEnum());
        cmd.setSuggestedLevelEnum(snapshot.getSuggestedLevelEnum());
        cmd.setLevelAdjustReason(snapshot.getLevelAdjustReason());
        cmd.setChangeReasonCode(snapshot.getChangeReasonCode());
        cmd.setChangeRemark(snapshot.getChangeRemark());
        cmd.setOrganizationId(snapshot.getOrganizationId());
        cmd.setLocationId(snapshot.getLocationId());
        cmd.setAssignedDoctorSnapshot(snapshot.getAssignedDoctorSnapshot());
        cmd.setDiseaseTypesJson(snapshot.getDiseaseTypesJson());
        cmd.setPatientSource(snapshot.getPatientSource());
        cmd.setIsGreenChannel(snapshot.getIsGreenChannel());
        cmd.setIsFreeRegister(snapshot.getIsFreeRegister());
        cmd.setQueuePriority(snapshot.getQueuePriority());
        cmd.setNote(snapshot.getNote());
        cmd.setTriageRegistrationModeEnum(snapshot.getTriageRegistrationModeEnum());
        cmd.setIsStaged(snapshot.getIsStaged());
        cmd.setStageQueueSeq(snapshot.getStageQueueSeq());
        cmd.setProvisionalTriageNo(snapshot.getProvisionalTriageNo());
        cmd.setSpecialPopulationTagsJson(snapshot.getSpecialPopulationTagsJson());
        cmd.setMciEventId(snapshot.getMciEventId());
        cmd.setBlacklistAlertAck(snapshot.getBlacklistAlertAck());
        cmd.setArrivalModeEnum(snapshot.getArrivalModeEnum());
        cmd.setTransferInOrgId(snapshot.getTransferInOrgId());
        cmd.setDestinationModeEnum(snapshot.getDestinationModeEnum());
        cmd.setSecondaryLocationId(snapshot.getSecondaryLocationId());
        cmd.setGreenChannelOnsetTime(snapshot.getGreenChannelOnsetTime());
        cmd.setEpidemicSurveyJson(snapshot.getEpidemicSurveyJson());
        cmd.setSpecialtyGreenChannelCode(snapshot.getSpecialtyGreenChannelCode());
        cmd.setAmbulance120Json(snapshot.getAmbulance120Json());
        cmd.setPolice110Json(snapshot.getPolice110Json());
        cmd.setMonitorDeviceBindingJson(snapshot.getMonitorDeviceBindingJson());
        cmd.setWristbandPrintedAt(wristbandPrintedAt);
        cmd.setVitalsAutoTierPolicyId(snapshot.getVitalsAutoTierPolicyId());
        cmd.setChiefComplaintOptional(Boolean.FALSE);
        cmd.setPriorEmgTriageId(snapshot.getPriorEmgTriageId());
        cmd.setAssessmentItems(mapAssessmentEntities(snapshot.getAssessments()));
        return cmd;
    }

    private List<SaveEmgTriageAssessmentItemCommand> mapAssessmentItems(List<SaveEmgTriageAssessmentItemDto> rows) {
        List<SaveEmgTriageAssessmentItemCommand> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (SaveEmgTriageAssessmentItemDto dto : rows) {
            if (dto == null) {
                continue;
            }
            SaveEmgTriageAssessmentItemCommand line = new SaveEmgTriageAssessmentItemCommand();
            line.setId(dto.getId());
            line.setAssessmentType(dto.getAssessmentType());
            line.setAssessmentData(dto.getAssessmentDataJson());
            line.setAssessmentScore(dto.getAssessmentScore());
            line.setAssessedAt(dto.getAssessedAt());
            line.setAssessedBy(dto.getAssessedBy());
            list.add(line);
        }
        return list;
    }

    private List<SaveEmgTriageAssessmentItemCommand> mapAssessmentEntities(List<EmgTriageAssessment> rows) {
        List<SaveEmgTriageAssessmentItemCommand> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (EmgTriageAssessment e : rows) {
            if (e == null) {
                continue;
            }
            SaveEmgTriageAssessmentItemCommand line = new SaveEmgTriageAssessmentItemCommand();
            line.setId(e.getId());
            line.setAssessmentType(e.getAssessmentType());
            line.setAssessmentData(e.getAssessmentData());
            line.setAssessmentScore(e.getAssessmentScore());
            line.setAssessedAt(e.getAssessedAt());
            line.setAssessedBy(e.getAssessedBy());
            list.add(line);
        }
        return list;
    }

    private List<PatientIdentifierItemCommand> mapIdentifierItems(List<PatientIdentifierItemDto> rows) {
        List<PatientIdentifierItemCommand> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (PatientIdentifierItemDto dto : rows) {
            if (dto == null) {
                continue;
            }
            PatientIdentifierItemCommand line = new PatientIdentifierItemCommand();
            line.setId(dto.getId());
            line.setTypeCode(dto.getTypeCode());
            line.setIdentifierNo(dto.getIdentifierNo());
            if (StringUtils.isNotBlank(dto.getStateEnum())) {
                IdentifierState st = EnumUtil.fromCode(IdentifierState.class, dto.getStateEnum());
                line.setStateEnum(st);
            }
            line.setStartTime(dto.getStartTime());
            line.setEndTime(dto.getEndTime());
            list.add(line);
        }
        return list;
    }
}
