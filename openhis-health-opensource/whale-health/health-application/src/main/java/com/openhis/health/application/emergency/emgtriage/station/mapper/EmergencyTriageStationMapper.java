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
import com.openhis.health.application.emergency.emgtriage.station.request.SaveOrSubmitEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SubmitSecondaryEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.AmendEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.VoidEmgTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.SearchPatientsForEmergencyTriageRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.PageEmgTriageWorkbenchRequest;
import com.openhis.health.application.emergency.emgtriage.station.request.RegisterOrUpdatePatientForEmergencyRequest;
import com.openhis.health.application.emergency.emgtriage.station.response.InitializeEmergencyTriageStationResponse;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.emergency.emgtriage.command.AmendEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.command.SaveEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.command.VoidEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.queries.EmgTriagePageQuery;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageByEncounterQueryModel;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageListItemQueryModel;
import com.openhis.health.domain.emergency.knowledge.queries.model.KnowledgeBaseItemQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 急诊分诊工作站请求/响应与领域命令、查询模型之间的字段级映射。
 *
 * <p>禁止承载业务流程分支，分支留在 {@code EmergencyTriageStationAppService}。</p>
 */
public interface EmergencyTriageStationMapper {

    /**
     * IF-EMG-06：保存分诊请求 → 领域命令。
     *
     * @param request    请求体
     * @param operatorId 操作人（来自 {@link com.openhis.whale.core.infrastructure.security.ICurrentUser}，不由客户端传）
     * @return 领域命令
     */
    SaveEmgTriageCommand toSaveEmgTriageCommand(SaveOrSubmitEmgTriageRequest request, Long operatorId);

    /**
     * IF-EMG-09：二次分诊请求 → 领域命令。
     *
     * @param request    请求体
     * @param operatorId 操作人
     * @return 领域命令（含 prior）
     */
    SaveEmgTriageCommand toSaveEmgTriageCommand(SubmitSecondaryEmgTriageRequest request, Long operatorId);

    /**
     * IF-EMG-07。
     *
     * @param request    请求体
     * @param operatorId 操作人（{@link com.openhis.whale.core.infrastructure.security.ICurrentUser}）
     * @return 修订命令
     */
    AmendEmgTriageCommand toAmendEmgTriageCommand(AmendEmgTriageRequest request, Long operatorId);

    /**
     * IF-EMG-08。
     *
     * @param request    请求体
     * @param operatorId 操作人
     * @return 作废命令
     */
    VoidEmgTriageCommand toVoidEmgTriageCommand(VoidEmgTriageRequest request, Long operatorId);

    /**
     * IF-EMG-02：检索请求 → 患者分页查询。
     *
     * @param request 请求体
     * @return 患者分页查询入参
     */
    PatientPagedQuery toPatientPagedQuery(SearchPatientsForEmergencyTriageRequest request);

    /**
     * IF-EMG-03：建档请求 → 注册命令。
     *
     * @param request 请求体
     * @return 注册命令
     */
    RegisterPatientCommand toRegisterPatientCommand(RegisterOrUpdatePatientForEmergencyRequest request);

    /**
     * IF-EMG-18：工作台分页查询。
     *
     * @param request 请求体
     * @return 分诊分页过滤条件
     */
    EmgTriagePageQuery toEmgTriagePageQuery(PageEmgTriageWorkbenchRequest request);

    /**
     * IF-EMG-18：列表行映射。
     *
     * @param row 查询模型行
     * @return 工作台 DTO
     */
    EmgTriageWorkbenchRowDto toWorkbenchRowDto(EmgTriageListItemQueryModel row);

    /**
     * 将 ACTIVE 分诊详情快照装配为 IF-EMG-06 同构请求体（工作站右侧编辑回显）。
     *
     * @param snap 分诊详情查询模型
     * @return 与保存接口一致的请求体
     */
    SaveOrSubmitEmgTriageRequest toSaveOrSubmitEmgTriageRequest(EmgTriageByEncounterQueryModel snap);

    /**
     * IF-EMG-13：知识库分页行映射。
     *
     * @param row 查询模型行
     * @return 候选行 DTO
     */
    KnowledgeCandidateRowDto toKnowledgeCandidateRowDto(KnowledgeBaseItemQueryModel row);

    /**
     * 知识库维护行映射。
     *
     * @param row 查询模型行
     * @return 维护行 DTO
     */
    com.openhis.health.application.emergency.emgtriage.station.dto.EmgKnowledgeBaseItemMaintenanceDto
            toEmgKnowledgeBaseItemMaintenanceDto(KnowledgeBaseItemQueryModel row);

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
    InitializeEmergencyTriageStationResponse toInitializeEmergencyTriageStationResponse(
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
            EmgTriageExtendedJsonFieldOptionsDto extendedJsonFieldOptions);

    /**
     * IF-EMG-14：腕带打印更新命令（基于当前 ACTIVE 分诊快照）。
     *
     * @param snapshot           就诊维度快照
     * @param operatorId         操作人
     * @param wristbandPrintedAt 打印时间
     * @return 保存命令
     */
    SaveEmgTriageCommand toSaveEmgTriageCommandForPrint(
            EmgTriageByEncounterQueryModel snapshot, Long operatorId, LocalDateTime wristbandPrintedAt);
}
