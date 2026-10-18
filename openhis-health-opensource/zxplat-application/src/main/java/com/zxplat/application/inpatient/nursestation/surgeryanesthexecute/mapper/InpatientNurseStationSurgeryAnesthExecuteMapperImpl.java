/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.surgeryanesthexecute.mapper;

import com.zxplat.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.zxplat.application.inpatient.nursestation.orderexecute.request.ExecuteNurseStationOrdersRequest;
import com.zxplat.application.inpatient.nursestation.orderexecute.response.ExecuteNurseStationOrdersResponse;
import com.zxplat.application.inpatient.nursestation.surgeryanesthexecute.dto.SurgeryAnesthExecuteDialogDto;
import com.zxplat.application.inpatient.nursestation.surgeryanesthexecute.request.SubmitSurgeryAnesthExecuteRequest;
import com.zxplat.application.inpatient.nursestation.surgeryanesthexecute.response.SubmitSurgeryAnesthExecuteResponse;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 手术/麻醉执行弹窗 Mapper 实现：SR/人员名 → DialogDto；Submit → EXE-02 写槽；EXE 响应 → SAE 响应。
 *
 * <p>仅做字段搬运，不承载类目 Gate、麻醉起止校验或计费编排。</p>
 */
@Component
public class InpatientNurseStationSurgeryAnesthExecuteMapperImpl
    implements InpatientNurseStationSurgeryAnesthExecuteMapper {

    /**
     * 组装执行弹窗 DTO（只读人员名由调用方预解析）。
     *
     * @param sr           服务申请列表模型（调用方保证非空）
     * @param planTime     计划槽位
     * @param activityName 项目名（可空）
     * @param nameById     医务人员主键→姓名
     * @return 弹窗 DTO；sr 为 null 时返回 null
     */
    @Override
    public SurgeryAnesthExecuteDialogDto toDialogDto(
        ServiceRequestListModel sr,
        LocalDateTime planTime,
        String activityName,
        Map<Long, String> nameById) {
        // 无申请行则无法组装弹窗
        if (sr == null) {
            return null;
        }
        Map<Long, String> names = nameById;
        // 调用方未预解析姓名时按空 Map 回落，避免 NPE
        if (names == null) {
            names = Collections.emptyMap();
        }
        SurgeryAnesthExecuteDialogDto dto = new SurgeryAnesthExecuteDialogDto();
        dto.setServiceRequestId(sr.getId());
        dto.setCategoryEnum(sr.getCategoryEnum());
        dto.setActivityName(activityName);
        // 手术时间默认带出申请；occurrence 用 planTime，二者分离
        dto.setProcedureTime(sr.getProcedureTime());
        dto.setSpecimen(null);
        dto.setNote(null);
        dto.setNurseId(sr.getNurseId());
        dto.setNurseName(names.get(sr.getNurseId()));
        // 麻醉起止医生开立不填，弹窗默认可空
        dto.setAnesthesiaStartTime(null);
        dto.setAnesthesiaEndTime(null);
        dto.setMainProcedureFlag(sr.getMainProcedureFlag());
        dto.setProcedureLevelCode(sr.getProcedureLevelCode());
        dto.setAnesthesiaMethodCode(sr.getAnesthesiaMethodCode());
        dto.setIncisionTypeCode(sr.getIncisionTypeCode());
        dto.setSurgeonId(sr.getSurgeonId());
        dto.setSurgeonName(names.get(sr.getSurgeonId()));
        dto.setFirstAssistantId(sr.getFirstAssistantId());
        dto.setFirstAssistantName(names.get(sr.getFirstAssistantId()));
        dto.setSecondAssistantId(sr.getSecondAssistantId());
        dto.setSecondAssistantName(names.get(sr.getSecondAssistantId()));
        dto.setAnesthetistId(sr.getAnesthetistId());
        dto.setAnesthetistName(names.get(sr.getAnesthetistId()));
        dto.setPlanTime(planTime);
        return dto;
    }

    /**
     * 提交请求 → EXE-02 单槽执行请求（含手术/麻醉弹窗标记与横表字段）。
     *
     * @param request 提交请求（调用方保证非空）
     * @return EXE-02 请求
     */
    @Override
    public ExecuteNurseStationOrdersRequest toExecuteOrdersRequest(SubmitSurgeryAnesthExecuteRequest request) {
        ExecuteNurseStationOrdersRequest executeRequest = new ExecuteNurseStationOrdersRequest();
        List<NurseStationOrderExecuteWriteSlotDto> items = new ArrayList<>();
        items.add(this.toWriteSlot(request));
        executeRequest.setItems(items);
        return executeRequest;
    }

    /**
     * EXE-02 响应 → SAE-02 响应。
     *
     * @param executeResponse EXE 执行结果（可空）
     * @return SAE 响应；入参 null 时返回空成功壳
     */
    @Override
    public SubmitSurgeryAnesthExecuteResponse toSubmitResponse(ExecuteNurseStationOrdersResponse executeResponse) {
        SubmitSurgeryAnesthExecuteResponse response = new SubmitSurgeryAnesthExecuteResponse();
        // EXE 未返回时按 0 成功计数与空软提示收口
        if (executeResponse == null) {
            response.setProcessedCount(Integer.valueOf(0));
            response.setWarnings(Collections.emptyList());
            return response;
        }
        response.setProcessedCount(executeResponse.getProcessedCount());
        List<String> warnings = executeResponse.getWarnings();
        // warnings 可空：统一为空列表，避免前端判 null
        if (warnings == null) {
            response.setWarnings(Collections.emptyList());
        } else {
            response.setWarnings(warnings);
        }
        return response;
    }

    /**
     * 提交请求 → 写槽位（含 surgeryAnesthExecuteFlag）。
     *
     * @param request 提交请求
     * @return 写槽位 DTO
     */
    @Override
    public NurseStationOrderExecuteWriteSlotDto toWriteSlot(SubmitSurgeryAnesthExecuteRequest request) {
        NurseStationOrderExecuteWriteSlotDto slot = new NurseStationOrderExecuteWriteSlotDto();
        // 空请求返回空槽，由 AppService Gate 拦截
        if (request == null) {
            return slot;
        }
        slot.setOrderId(request.getServiceRequestId());
        slot.setOrderType(ReadModelOrderTypeConstants.SERVICE_REQUEST);
        slot.setPlanTime(request.getPlanTime());
        slot.setActualTime(request.getActualTime());
        slot.setAccountId(request.getAccountId());
        slot.setBindItems(request.getBindItems());
        // 标记弹窗路径：EXE 写横表 + 多角色 performer（BR-IP-SAE-003～007）
        slot.setSurgeryAnesthExecuteFlag(Boolean.TRUE);
        slot.setProcedureTime(request.getProcedureTime());
        slot.setSpecimen(request.getSpecimen());
        slot.setNote(request.getNote());
        slot.setNurseId(request.getNurseId());
        slot.setAnesthesiaStartTime(request.getAnesthesiaStartTime());
        slot.setAnesthesiaEndTime(request.getAnesthesiaEndTime());
        return slot;
    }
}
