/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.clinical.allergyhistory.mapper;

import com.zxplat.application.clinical.allergyhistory.dto.PatientAllergyHistoryEnumOptionDto;
import com.zxplat.application.clinical.allergyhistory.dto.PatientAllergyHistoryRowDto;
import com.zxplat.application.clinical.allergyhistory.request.SavePatientAllergyHistoryRequest;
import com.zxplat.application.clinical.allergyhistory.request.SavePatientNkdaRequest;
import com.zxplat.application.clinical.allergyhistory.response.DeletePatientAllergyHistoryResponse;
import com.zxplat.application.clinical.allergyhistory.response.ListPatientAllergyHistoryResponse;
import com.zxplat.application.clinical.allergyhistory.response.LoadPatientAllergyHistoryInitResponse;
import com.zxplat.application.clinical.allergyhistory.response.SavePatientAllergyHistoryResponse;
import com.zxplat.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.zxplat.domain.clinical.allergyintolerance.command.SaveDeclaredAllergyRecordCommand;
import com.zxplat.domain.clinical.allergyintolerance.command.SaveNkdaCommand;
import com.zxplat.domain.clinical.allergyintolerance.queries.model.AllergyHistoryFlagQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link PatientAllergyHistoryMapper} 默认实现。
 */
@Component
public class PatientAllergyHistoryMapperImpl implements PatientAllergyHistoryMapper {

    /**
     * 组装枚举选项。
     *
     * @param code        持久化 code
     * @param displayText 展示文案
     * @return 选项
     */
    @Override
    public PatientAllergyHistoryEnumOptionDto toEnumOption(String code, String displayText) {
        PatientAllergyHistoryEnumOptionDto option = new PatientAllergyHistoryEnumOptionDto();
        option.setCode(code);
        option.setDisplayText(displayText);
        return option;
    }

    /**
     * 包装表单选项。
     *
     * @param categoryOptions           类别
     * @param verificationStatusOptions 验证状态
     * @param severityOptions            严重程度
     * @param criticalityOptions         危险程度
     * @return 初始化响应
     */
    @Override
    public LoadPatientAllergyHistoryInitResponse toInitResponse(
        List<PatientAllergyHistoryEnumOptionDto> categoryOptions,
        List<PatientAllergyHistoryEnumOptionDto> verificationStatusOptions,
        List<PatientAllergyHistoryEnumOptionDto> severityOptions,
        List<PatientAllergyHistoryEnumOptionDto> criticalityOptions) {
        LoadPatientAllergyHistoryInitResponse response = new LoadPatientAllergyHistoryInitResponse();
        response.setCategoryOptions(this.emptyIfNull(categoryOptions));
        response.setVerificationStatusOptions(this.emptyIfNull(verificationStatusOptions));
        response.setSeverityOptions(this.emptyIfNull(severityOptions));
        response.setCriticalityOptions(this.emptyIfNull(criticalityOptions));
        return response;
    }

    /**
     * 包装列表与摘要。
     *
     * @param flag 过敏史标记
     * @param rows 列表行
     * @return 列表响应
     */
    @Override
    public ListPatientAllergyHistoryResponse toListResponse(
        AllergyHistoryFlagQueryModel flag,
        List<PatientAllergyHistoryRowDto> rows) {
        ListPatientAllergyHistoryResponse response = new ListPatientAllergyHistoryResponse();
        if (flag == null) {
            response.setHistoryFlag(Boolean.FALSE);
            response.setNkdaFlag(Boolean.FALSE);
            response.setSummaryText("未登记");
        } else {
            response.setHistoryFlag(flag.getHistoryFlag());
            response.setNkdaFlag(flag.getNkdaFlag());
            response.setSummaryText(flag.getSummaryText());
        }
        response.setRows(this.emptyIfNull(rows));
        return response;
    }

    /**
     * 保存请求映射为主诉写命令。
     *
     * @param request        保存请求
     * @param practitionerId 记录者
     * @return 命令
     */
    @Override
    public SaveDeclaredAllergyRecordCommand toSaveDeclaredCommand(
        SavePatientAllergyHistoryRequest request,
        Long practitionerId) {
        SaveDeclaredAllergyRecordCommand cmd = new SaveDeclaredAllergyRecordCommand();
        cmd.setId(request.getId());
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(request.getPatientId());
        cmd.setCategoryCode(request.getCategoryCode());
        cmd.setDescription(request.getDescription());
        cmd.setClinicalStatusEnum(request.getClinicalStatusCode());
        cmd.setVerificationStatusEnum(request.getVerificationStatusCode());
        cmd.setSeverityEnum(request.getSeverityCode());
        cmd.setCriticalityEnum(request.getCriticalityCode());
        cmd.setOnsetDateTime(request.getOnsetDateTime());
        cmd.setPractitionerId(practitionerId);
        cmd.setNote(request.getNote());
        return cmd;
    }

    /**
     * NKDA 请求映射为写命令。
     *
     * @param request        请求
     * @param practitionerId 记录者
     * @return 命令
     */
    @Override
    public SaveNkdaCommand toSaveNkdaCommand(SavePatientNkdaRequest request, Long practitionerId) {
        SaveNkdaCommand cmd = new SaveNkdaCommand();
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(request.getPatientId());
        cmd.setVerificationStatusEnum(request.getVerificationStatusCode());
        cmd.setPractitionerId(practitionerId);
        return cmd;
    }

    /**
     * 构造删除命令。
     *
     * @param allergyRecordId 记录主键
     * @param operatorId       操作人
     * @return 命令
     */
    @Override
    public DeleteSkinTestRecordCommand toDeleteCommand(Long allergyRecordId, Long operatorId) {
        DeleteSkinTestRecordCommand cmd = new DeleteSkinTestRecordCommand();
        cmd.setRecordId(allergyRecordId);
        cmd.setOperatorId(operatorId);
        return cmd;
    }

    /**
     * 构造保存响应。
     *
     * @param allergyRecordId 记录主键
     * @return 保存响应
     */
    @Override
    public SavePatientAllergyHistoryResponse toSaveResponse(Long allergyRecordId) {
        SavePatientAllergyHistoryResponse response = new SavePatientAllergyHistoryResponse();
        response.setAllergyRecordId(allergyRecordId);
        return response;
    }

    /**
     * 构造删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    @Override
    public DeletePatientAllergyHistoryResponse toDeleteResponse(boolean success) {
        DeletePatientAllergyHistoryResponse response = new DeletePatientAllergyHistoryResponse();
        response.setSuccess(success);
        return response;
    }

    /**
     * 空列表兜底，避免响应 rows 为 null。
     *
     * @param source 源列表
     * @param <T>    元素类型
     * @return 非 null 列表
     */
    private <T> List<T> emptyIfNull(List<T> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        return source;
    }
}
