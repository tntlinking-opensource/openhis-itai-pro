/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.mapper;

import com.zxplat.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisRow;
import com.zxplat.application.inpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.zxplat.application.inpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.zxplat.application.inpatient.doctorstation.diagnosis.request.DeleteEncounterDiagnosisRequest;
import com.zxplat.application.inpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.zxplat.application.inpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.zxplat.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.zxplat.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.zxplat.application.inpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.zxplat.application.inpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.zxplat.application.inpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.zxplat.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 住院医生站诊断录入：Request/Command/读模型与 Response 之间的字段装配及住院写读前门控。
 *
 * <p>禁止承载领域不变量；校验失败由调用方以 {@code BusinessException} 表达。</p>
 */
public interface InpatientDoctorStationDiagnosisMapper {

    /**
     * Gate：校验请求患者与住院读模型快照患者一致，防止串患者上下文。
     *
     * @param snapshot         住院信息读模型快照
     * @param requestPatientId 请求携带的患者标识
     */
    void validateInpatientEncounterPatientGate(InpatientEncounterContextQueryModel snapshot, Long requestPatientId);

    /**
     * Gate：校验就诊类别为住院（IMP）。
     *
     * @param snapshot 住院信息读模型快照
     */
    void validateInpatientEncounterClassImp(InpatientEncounterContextQueryModel snapshot);

    /**
     * Gate：校验住院就诊上下文可用于维护诊断（仅要求读模型存在；允许后补，不按在院状态拦截）。
     *
     * @param snapshot 住院信息读模型快照
     */
    void validateInpatientEncounterWritableGate(InpatientEncounterContextQueryModel snapshot);

    /**
     * Gate：校验批次内非空患者疾病诊断类型码均合法。
     *
     * @param request 保存诊断批次请求
     */
    void validateIptDiseaseTypeCodesInBatch(SaveEncounterDiagnosisRequest request);

    /**
     * 将读模型中的诊断快照 JSON 反序列化为列表。
     *
     * @param diagnosisJson 快照 JSON，允许 null 或空白
     * @return 行 DTO 列表；空快照返回空列表
     */
    List<EncounterDiagnosisRowDto> parseDiagnosisJson(String diagnosisJson);

    /**
     * 将门诊侧诊断行 DTO 拷贝为住院 IF 出参行（字段契约一致，仅包名不同）。
     *
     * @param source 实时拼装行，允许 null 或空
     * @return 住院包诊断行列表，永不为 null
     */
    List<EncounterDiagnosisRowDto> copyDiagnosisRows(
        List<com.zxplat.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto> source);

    /**
     * 装配 IF-IP-DOR-02 响应。
     *
     * @param rows 诊断行集合
     * @return 响应
     */
    EncounterDiagnosisResponse toEncounterDiagnosisResponse(List<EncounterDiagnosisRowDto> rows);

    /**
     * 装配 IF-IP-DOR-03 响应。
     *
     * @param patientHistoryList  病人历史
     * @param doctorCommonUseList 医生常用
     * @param userPersonalList    用户个人
     * @param organizationList    科室
     * @return 响应
     */
    CommonDiagnosesResponse toCommonDiagnosesResponse(
        List<CommonDiagnosisRowDto> patientHistoryList,
        List<CommonDiagnosisRowDto> doctorCommonUseList,
        List<CommonDiagnosisRowDto> userPersonalList,
        List<CommonDiagnosisRowDto> organizationList,
        List<OptionDto> iptDiseaseTypeOptions);

    /**
     * 将诊断中西医维度筛选入参解析为目录 {@code sourceEnum} 字符串集合。
     *
     * @param diagnosisKindCode {@code 1}/{@code 2} 或 null
     * @return null 表示不按来源过滤；非空表示 IN 集合
     */
    List<String> parseDiagnosisKindSourceFilter(String diagnosisKindCode);

    /**
     * 将读侧行模型装配为分页行。
     *
     * @param model               读模型行
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
}
