/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisRow;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.OpspFilingDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.PatientOpspFilingDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.DeleteEncounterDiagnosisRequest;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.OpspDiseCodeDefinitionMatchQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingLineSnapshot;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 医生站诊断录入：Request/Command/读模型与 Response 之间的字段装配。
 *
 * <p>禁止承载领域不变量与写前门禁；校验失败由调用方以 {@code BusinessException} 表达。</p>
 */
public interface DoctorStationDiagnosisMapper {

    /**
     * Gate：校验请求患者与读模型快照患者一致，防止串患者上下文。
     *
     * @param snapshot        门诊信息读模型快照
     * @param requestPatientId 请求携带的患者标识
     * @throws com.openhis.whale.core.application.exception.BusinessException 不一致时抛出（医生站口径 EX-DOD-104）
     */
    void validateEncounterPatientGate(OutpatientEncounterContextQueryModel snapshot, Long requestPatientId);

    /**
     * 将读模型中的诊断快照 JSON 反序列化为列表。
     *
     * @param diagnosisJson 快照 JSON，允许 null 或空白
     * @return 行 DTO 列表；空快照返回空列表
     */
    List<EncounterDiagnosisRowDto> parseDiagnosisJson(String diagnosisJson);

    /**
     * 装配 IF-DOR-06 响应。
     *
     * @param rows 诊断行集合
     * @return 响应
     */
    EncounterDiagnosisResponse toEncounterDiagnosisResponse(List<EncounterDiagnosisRowDto> rows);

    /**
     * 装配 IF-DOR-06A 响应。
     *
     * @param patientHistoryList 病人历史
     * @param doctorCommonUseList 医生常用
     * @param userPersonalList    用户个人
     * @param organizationList    科室
     * @param medicalTypeOptions  门诊诊断类型下拉（YbMedType 门诊向子集）
     * @return 响应
     */
    CommonDiagnosesResponse toCommonDiagnosesResponse(
        List<CommonDiagnosisRowDto> patientHistoryList,
        List<CommonDiagnosisRowDto> doctorCommonUseList,
        List<CommonDiagnosisRowDto> userPersonalList,
        List<CommonDiagnosisRowDto> organizationList,
        List<OptionDto> medicalTypeOptions);

    /**
     * 将诊断中西医维度筛选入参解析为目录 {@code sourceEnum} 字符串集合。
     *
     * @param diagnosisKindCode {@code 1}/{@code 2} 或 null
     * @return null 表示不按来源过滤；非空表示 IN 集合
     */
    List<String> parseDiagnosisKindSourceFilter(String diagnosisKindCode);

    /**
     * 将读侧行模型装配为分页行（补充展示字段依赖外部传入字典映射）。
     *
     * @param model            读模型行
     * @param sourceDisplayByCode 来源码→展示文案
     * @return 响应行
     */
    ConditionDefinitionRowResponse toConditionDefinitionRowResponse(
        DoctorStationConditionDefinitionRowModel model,
        Map<String, String> sourceDisplayByCode);

    /**
     * 装配分页包装。
     *
     * @param page    读侧分页
     * @param records 已装配行
     * @return 分页响应
     */
    ConditionDefinitionPageResponse toConditionDefinitionPageResponse(
        Page<DoctorStationConditionDefinitionRowModel> page,
        List<ConditionDefinitionRowResponse> records);

    /**
     * Gate：保存批次业务校验（非空、主诊唯一、排序唯一等）。
     *
     * @param request 保存请求
     * @throws com.openhis.whale.core.application.exception.BusinessException 参数非法
     */
    void validateSaveEncounterDiagnosisRequest(SaveEncounterDiagnosisRequest request);

    /**
     * 组装就诊诊断关联批量 upsert 命令（直挂 conditionDefinitionId + encounterDiagnosisId）。
     *
     * @param request    原始请求
     * @param operatorId 操作人
     * @return 关联批量命令
     */
    BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertEncounterDiagnosisAssociationCommand(
        SaveEncounterDiagnosisRequest request,
        Long operatorId);

    /**
     * 装配保存响应。
     *
     * @param savedCount               成功条数
     * @param savedEncounterDiagnosisIds 就诊诊断行 id
     * @return 响应
     */
    SaveEncounterDiagnosisResponse toSaveEncounterDiagnosisResponse(int savedCount, List<Long> savedEncounterDiagnosisIds);

    /**
     * 解析删除请求中的就诊诊断行主键。
     *
     * @param request 删除请求
     * @return 就诊诊断行主键
     */
    Long resolveDeleteEncounterDiagnosisId(DeleteEncounterDiagnosisRequest request);

    /**
     * Gate：删除请求参数校验。
     *
     * @param encounterDiagnosisId 就诊诊断行 id
     * @param encounterId          就诊 id
     */
    void validateDeleteEncounterDiagnosisRequest(Long encounterDiagnosisId, Long encounterId);

    /**
     * 装配删除响应。
     *
     * @param success 是否成功
     * @param message 提示
     * @return 响应
     */
    DeleteEncounterDiagnosisResponse toDeleteEncounterDiagnosisResponse(boolean success, String message);

    /**
     * 将诊断定义读模型行转为常用诊断快捷行。
     *
     * @param model               目录行
     * @param sourceDisplayByCode 来源码→展示文案
     * @return 常用诊断行
     */
    CommonDiagnosisRowDto toCommonDiagnosisRowDto(
        DoctorStationConditionDefinitionRowModel model,
        Map<String, String> sourceDisplayByCode);

    /**
     * 将患者历史合并读侧行转为常用诊断快捷行。
     *
     * @param model               历史读侧行
     * @param sourceDisplayByCode 来源码→展示文案
     * @return 常用诊断行
     */
    CommonDiagnosisRowDto toCommonDiagnosisRowDto(
        PatientHistoryCommonDiagnosisRow model,
        Map<String, String> sourceDisplayByCode);

    /**
     * 装配 IF-DOR-06B 响应。
     *
     * @param opspFilingImportEnabled CHS 门控
     * @param filingList              备案行
     * @return 响应
     */
    PatientOpspFilingDiagnosisResponse toPatientOpspFilingDiagnosisResponse(
        boolean opspFilingImportEnabled,
        List<OpspFilingDiagnosisRowDto> filingList);

    /**
     * 将平台备案行与目录匹配结果装配为医生站候选行。
     *
     * @param filingLine  5301 备案行
     * @param match       目录匹配；未命中可空
     * @param matchStatus 映射状态码
     * @param matchHint   提示文案
     * @param valid       是否有效期内
     * @return 候选行
     */
    OpspFilingDiagnosisRowDto toOpspFilingDiagnosisRowDto(
        InsuranceOpspFilingLineSnapshot filingLine,
        OpspDiseCodeDefinitionMatchQueryModel match,
        String matchStatus,
        String matchHint,
        boolean valid);
}
