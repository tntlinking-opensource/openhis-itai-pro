/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.mapper;

import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.dto.AdmissionDiagnosisSubmitDto;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.request.RequestInpatientAdmissionRequest;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response.CheckInpatientAdmissionExistsResponse;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response.RequestInpatientAdmissionResponse;
import com.openhis.health.domain.administration.encounter.command.AdmissionConditionItem;
import com.openhis.health.domain.administration.encounter.command.RequestInpatientAdmissionCommand;
import com.openhis.health.domain.administration.encounter.queries.model.OpenInpatientLineQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DoctorStationInpatientAdmissionMapper} 的 Spring 装配实现：字段级搬运，不含业务分支。
 */
@Component
public class DoctorStationInpatientAdmissionMapperImpl implements DoctorStationInpatientAdmissionMapper {

    /**
     * IF-IP-REQ-01（Cmd 入参）：请求体 → 发起住院申请领域命令。
     *
     * @param request            应用层请求；调用方保证非空且已通过 Gate/Pre
     * @param resolvedPatientId  由源门诊就诊解析的患者主键
     * @return 单条 item 的批量命令
     */
    @Override
    public RequestInpatientAdmissionCommand toRequestInpatientAdmissionCommand(
        RequestInpatientAdmissionRequest request,
        Long resolvedPatientId
    ) {
        if (request == null) {
            return null;
        }
        RequestInpatientAdmissionCommand command = new RequestInpatientAdmissionCommand();
        command.setOperatorId(request.getOperatorId());
        RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand item =
            new RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand();
        item.setAmbEncounterId(request.getAmbEncounterId());
        item.setPatientId(resolvedPatientId);
        item.setTargetDepartmentId(request.getTargetDepartmentId());
        item.setWardId(request.getWardId());
        item.setRequestDoctorId(request.getRequestDoctorId());
        item.setAdmitSourceCode(request.getAdmitSourceCode());
        item.setInWayCode(request.getInWayCode());
        item.setPriorityCode(request.getPriorityCode());
        item.setAdmissionDiagnoses(this.toAdmissionConditionItems(request.getAdmissionDiagnoses()));
        command.setItem(item);
        return command;
    }

    /**
     * IF-IP-REQ-01（Asm）：写侧结果 → 提交响应。
     *
     * @param encounterId 新建住院就诊主键
     * @param busNo       住院业务号
     * @param success     是否成功
     * @return 响应体
     */
    @Override
    public RequestInpatientAdmissionResponse toRequestInpatientAdmissionResponse(
        Long encounterId,
        String busNo,
        boolean success
    ) {
        RequestInpatientAdmissionResponse response = new RequestInpatientAdmissionResponse();
        response.setInpatientEncounterId(encounterId);
        response.setInpatientEncounterBusNo(busNo);
        response.setSuccess(success);
        return response;
    }

    /**
     * IF-IP-REQ-02（Asm）：只读查询模型 → 是否存在住院线响应。
     *
     * @param query 未取消住院线查询结果；调用方保证非 null
     * @return 响应体
     */
    @Override
    public CheckInpatientAdmissionExistsResponse toCheckInpatientAdmissionExistsResponse(OpenInpatientLineQuery query) {
        if (query == null) {
            return null;
        }
        CheckInpatientAdmissionExistsResponse response = new CheckInpatientAdmissionExistsResponse();
        response.setExists(query.isExists());
        response.setInpatientEncounterId(query.getEncounterId());
        response.setStatusEnum(query.getStatusEnum());
        return response;
    }

    /**
     * 入院诊断提交行 → 领域命令项列表。
     *
     * @param rows 应用层诊断行；调用方保证非 null 且元素非 null（Gate 已校验）
     * @return 领域诊断项列表，无行时返回空表
     */
    private List<AdmissionConditionItem> toAdmissionConditionItems(List<AdmissionDiagnosisSubmitDto> rows) {
        List<AdmissionConditionItem> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (AdmissionDiagnosisSubmitDto row : rows) {
            AdmissionConditionItem item = new AdmissionConditionItem();
            // 住院申请一律新建住院线诊断关联，不复用门诊就诊诊断行主键（防跨就诊 upsert）
            item.setEncounterDiagnosisId(null);
            item.setConditionDefinitionId(row.getConditionDefinitionId());
            item.setPrimaryFlag(row.getPrimaryFlag());
            item.setDescription(row.getDescription());
            item.setTcmFlag(row.getTcmFlag());
            item.setTcmSyndromeGroup(row.getTcmSyndromeGroup());
            items.add(item);
        }
        return items;
    }
}
