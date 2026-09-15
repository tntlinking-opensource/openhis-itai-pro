/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.mapper;

import com.openhis.health.application.clinical.allergyhistory.dto.PatientAllergyHistoryEnumOptionDto;
import com.openhis.health.application.clinical.allergyhistory.dto.PatientAllergyHistoryRowDto;
import com.openhis.health.application.clinical.allergyhistory.request.SavePatientAllergyHistoryRequest;
import com.openhis.health.application.clinical.allergyhistory.request.SavePatientNkdaRequest;
import com.openhis.health.application.clinical.allergyhistory.response.DeletePatientAllergyHistoryResponse;
import com.openhis.health.application.clinical.allergyhistory.response.ListPatientAllergyHistoryResponse;
import com.openhis.health.application.clinical.allergyhistory.response.LoadPatientAllergyHistoryInitResponse;
import com.openhis.health.application.clinical.allergyhistory.response.SavePatientAllergyHistoryResponse;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveDeclaredAllergyRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveNkdaCommand;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyHistoryFlagQueryModel;

import java.util.List;

/**
 * 患者过敏史 Request/Response 与领域 Command 映射。
 */
public interface PatientAllergyHistoryMapper {

    /**
     * 框架选项转为过敏史枚举选项。
     *
     * @param code         持久化 code
     * @param displayText  展示文案
     * @return 枚举选项
     */
    PatientAllergyHistoryEnumOptionDto toEnumOption(String code, String displayText);

    /**
     * 构造初始化响应。
     *
     * @param categoryOptions            类别
     * @param verificationStatusOptions  验证状态
     * @param severityOptions             严重程度
     * @param criticalityOptions          危险程度
     * @return 初始化响应
     */
    LoadPatientAllergyHistoryInitResponse toInitResponse(
        List<PatientAllergyHistoryEnumOptionDto> categoryOptions,
        List<PatientAllergyHistoryEnumOptionDto> verificationStatusOptions,
        List<PatientAllergyHistoryEnumOptionDto> severityOptions,
        List<PatientAllergyHistoryEnumOptionDto> criticalityOptions);

    /**
     * 构造列表响应。
     *
     * @param flag 过敏史标记
     * @param rows 列表行
     * @return 列表响应
     */
    ListPatientAllergyHistoryResponse toListResponse(
        AllergyHistoryFlagQueryModel flag,
        List<PatientAllergyHistoryRowDto> rows);

    /**
     * 保存请求转主诉命令。
     *
     * @param request       保存请求
     * @param practitionerId 记录者
     * @return 命令
     */
    SaveDeclaredAllergyRecordCommand toSaveDeclaredCommand(
        SavePatientAllergyHistoryRequest request,
        Long practitionerId);

    /**
     * NKDA 请求转命令。
     *
     * @param request       请求
     * @param practitionerId 记录者
     * @return 命令
     */
    SaveNkdaCommand toSaveNkdaCommand(SavePatientNkdaRequest request, Long practitionerId);

    /**
     * 删除命令。
     *
     * @param allergyRecordId 记录主键
     * @param operatorId       操作人
     * @return 命令
     */
    DeleteSkinTestRecordCommand toDeleteCommand(Long allergyRecordId, Long operatorId);

    /**
     * 保存响应。
     *
     * @param allergyRecordId 记录主键
     * @return 保存响应
     */
    SavePatientAllergyHistoryResponse toSaveResponse(Long allergyRecordId);

    /**
     * 删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    DeletePatientAllergyHistoryResponse toDeleteResponse(boolean success);
}
