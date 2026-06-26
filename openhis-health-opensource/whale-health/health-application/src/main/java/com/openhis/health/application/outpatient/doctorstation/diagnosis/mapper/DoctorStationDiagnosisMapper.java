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

package com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper;

import com.openhis.health.module.shared.diagnosis.PatientHistoryCommonDiagnosisRow;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
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
     * @return 响应
     */
    CommonDiagnosesResponse toCommonDiagnosesResponse(
        List<CommonDiagnosisRowDto> patientHistoryList,
        List<CommonDiagnosisRowDto> doctorCommonUseList,
        List<CommonDiagnosisRowDto> userPersonalList,
        List<CommonDiagnosisRowDto> organizationList);

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
     * Request → 保存诊断事实命令。
     *
     * @param request    请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    SaveDoctorDiagnosisCommand toSaveDoctorDiagnosisCommand(SaveEncounterDiagnosisRequest request, Long operatorId);

    /**
     * 保存事实后组装就诊诊断关联批量命令（与请求行顺序一致）。
     *
     * @param request            原始请求
     * @param savedConditionIds  与请求子项顺序一致的诊断事实 id 列表
     * @param operatorId         操作人
     * @return 关联批量命令
     */
    BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertEncounterDiagnosisAssociationCommand(
        SaveEncounterDiagnosisRequest request,
        List<Long> savedConditionIds,
        Long operatorId);

    /**
     * 装配保存响应。
     *
     * @param savedCount        成功条数
     * @param savedConditionIds 诊断事实 id
     * @return 响应
     */
    SaveEncounterDiagnosisResponse toSaveEncounterDiagnosisResponse(int savedCount, List<Long> savedConditionIds);

    /**
     * Gate：删除请求参数校验。
     *
     * @param conditionId 诊断事实 id
     * @param encounterId 就诊 id
     */
    void validateDeleteEncounterDiagnosisRequest(Long conditionId, Long encounterId);

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
