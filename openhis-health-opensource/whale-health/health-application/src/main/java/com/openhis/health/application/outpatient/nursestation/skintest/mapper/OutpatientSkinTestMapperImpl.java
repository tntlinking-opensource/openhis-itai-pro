/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.mapper;

import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestRowDto;
import com.openhis.health.application.outpatient.nursestation.skintest.request.SaveOutpatientSkinTestRecordRequest;
import com.openhis.health.application.outpatient.nursestation.skintest.response.DeleteOutpatientSkinTestRecordResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.ListOutpatientSkinTestRowsResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.LoadOutpatientSkinTestInitResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.SaveOutpatientSkinTestRecordResponse;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link OutpatientSkinTestMapper} 默认实现。
 */
@Component
public class OutpatientSkinTestMapperImpl implements OutpatientSkinTestMapper {

    /**
     * 透传已装配的初始化响应。
     *
     * @param response 已填充选项的响应
     * @return 同一响应实例
     */
    @Override
    public LoadOutpatientSkinTestInitResponse toInitResponse(LoadOutpatientSkinTestInitResponse response) {
        return response;
    }

    /**
     * 包装皮试列表行。
     *
     * @param rows 列表行
     * @return 列表响应
     */
    @Override
    public ListOutpatientSkinTestRowsResponse toListResponse(List<OutpatientSkinTestRowDto> rows) {
        ListOutpatientSkinTestRowsResponse response = new ListOutpatientSkinTestRowsResponse();
        if (rows == null) {
            response.setRows(new ArrayList<>());
        } else {
            response.setRows(rows);
        }
        return response;
    }

    /**
     * 将护士站保存请求映射为过敏记录写命令。
     *
     * @param request 保存请求
     * @param skinTestServiceRequestId 皮试检查 SR 主键
     * @return 保存命令
     */
    @Override
    public SaveSkinTestRecordCommand toSaveSkinTestRecordCommand(
        SaveOutpatientSkinTestRecordRequest request,
        Long skinTestServiceRequestId) {
        SaveSkinTestRecordCommand cmd = new SaveSkinTestRecordCommand();
        cmd.setId(request.getAllergyRecordId());
        cmd.setRequestId(skinTestServiceRequestId);
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(request.getPatientId());
        cmd.setMedicationId(request.getMedicationId());
        cmd.setLotNumber(request.getLotNumber());
        cmd.setClinicalStatusEnum(request.getClinicalStatusCode());
        cmd.setVerificationStatusEnum(request.getVerificationStatusCode());
        cmd.setCriticalityEnum(request.getCriticalityCode());
        cmd.setSeverityEnum(request.getSeverityCode());
        cmd.setDescription(request.getDescription());
        cmd.setPractitionerId(request.getPractitionerId());
        cmd.setCheckPractitionerId(request.getCheckPractitionerId());
        if (request.getRecordedDate() != null) {
            cmd.setRecordedDate(request.getRecordedDate());
        } else {
            cmd.setRecordedDate(LocalDateTime.now());
        }
        return cmd;
    }

    /**
     * 构造保存成功响应。
     *
     * @param allergyRecordId 过敏记录主键
     * @param skinTestServiceRequestId 皮试检查 SR 主键
     * @return 保存响应
     */
    @Override
    public SaveOutpatientSkinTestRecordResponse toSaveResponse(Long allergyRecordId, Long skinTestServiceRequestId) {
        SaveOutpatientSkinTestRecordResponse response = new SaveOutpatientSkinTestRecordResponse();
        response.setAllergyRecordId(allergyRecordId);
        response.setSkinTestServiceRequestId(skinTestServiceRequestId);
        return response;
    }

    /**
     * 构造删除命令。
     *
     * @param allergyRecordId 过敏记录主键
     * @param operatorId 操作人
     * @return 删除命令
     */
    @Override
    public DeleteSkinTestRecordCommand toDeleteSkinTestRecordCommand(Long allergyRecordId, Long operatorId) {
        DeleteSkinTestRecordCommand cmd = new DeleteSkinTestRecordCommand();
        cmd.setRecordId(allergyRecordId);
        cmd.setOperatorId(operatorId);
        return cmd;
    }

    /**
     * 构造删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    @Override
    public DeleteOutpatientSkinTestRecordResponse toDeleteResponse(boolean success) {
        DeleteOutpatientSkinTestRecordResponse response = new DeleteOutpatientSkinTestRecordResponse();
        response.setSuccess(success);
        return response;
    }
}
