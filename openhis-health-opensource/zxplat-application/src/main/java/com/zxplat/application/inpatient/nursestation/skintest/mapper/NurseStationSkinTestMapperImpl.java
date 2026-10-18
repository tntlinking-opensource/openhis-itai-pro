/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.skintest.mapper;

import com.zxplat.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestRowDto;
import com.zxplat.application.inpatient.nursestation.skintest.request.SaveNurseStationSkinTestRecordRequest;
import com.zxplat.application.inpatient.nursestation.skintest.response.DeleteNurseStationSkinTestRecordResponse;
import com.zxplat.application.inpatient.nursestation.skintest.response.ListNurseStationSkinTestRowsResponse;
import com.zxplat.application.inpatient.nursestation.skintest.response.LoadNurseStationSkinTestInitResponse;
import com.zxplat.application.inpatient.nursestation.skintest.response.SaveNurseStationSkinTestRecordResponse;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.zxplat.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link NurseStationSkinTestMapper} 默认实现。
 */
@Component
public class NurseStationSkinTestMapperImpl implements NurseStationSkinTestMapper {

    /**
     * 透传已装配的初始化响应。
     *
     * @param response 已填充选项的响应
     * @return 同一响应实例
     */
    @Override
    public LoadNurseStationSkinTestInitResponse toInitResponse(LoadNurseStationSkinTestInitResponse response) {
        return response;
    }

    /**
     * 护士实体列表转下拉选项。
     *
     * @param practitioners 护士实体列表
     * @return 下拉选项
     */
    @Override
    public List<OptionDto> toNurseOptionList(List<Practitioner> practitioners) {
        List<OptionDto> list = new ArrayList<>();
        if (practitioners == null) {
            return list;
        }
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            Long id = practitioner.getId();
            String label = practitioner.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 包装皮试列表行。
     *
     * @param rows 列表行
     * @return 列表响应
     */
    @Override
    public ListNurseStationSkinTestRowsResponse toListResponse(List<NurseStationSkinTestRowDto> rows) {
        ListNurseStationSkinTestRowsResponse response = new ListNurseStationSkinTestRowsResponse();
        if (rows == null) {
            response.setRows(new ArrayList<>());
        } else {
            response.setRows(rows);
        }
        return response;
    }

    /**
     * 组装住院护士站可见性查询（队列 + 病区 + 就诊收窄）。
     *
     * @param encounterIds   目标就诊
     * @param managedWardIds 管辖病区
     * @return 住院上下文查询
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(
        List<Long> encounterIds,
        List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * 将护士站保存请求映射为过敏记录写命令。
     *
     * @param request                  保存请求
     * @param skinTestServiceRequestId 皮试检查 SR 主键
     * @return 保存命令
     */
    @Override
    public SaveSkinTestRecordCommand toSaveSkinTestRecordCommand(
        SaveNurseStationSkinTestRecordRequest request,
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
     * @param allergyRecordId          过敏记录主键
     * @param skinTestServiceRequestId 皮试检查 SR 主键
     * @return 保存响应
     */
    @Override
    public SaveNurseStationSkinTestRecordResponse toSaveResponse(
        Long allergyRecordId,
        Long skinTestServiceRequestId) {
        SaveNurseStationSkinTestRecordResponse response = new SaveNurseStationSkinTestRecordResponse();
        response.setAllergyRecordId(allergyRecordId);
        response.setSkinTestServiceRequestId(skinTestServiceRequestId);
        return response;
    }

    /**
     * 构造删除命令。
     *
     * @param allergyRecordId 过敏记录主键
     * @param operatorId      操作人
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
    public DeleteNurseStationSkinTestRecordResponse toDeleteResponse(boolean success) {
        DeleteNurseStationSkinTestRecordResponse response = new DeleteNurseStationSkinTestRecordResponse();
        response.setSuccess(success);
        return response;
    }
}
