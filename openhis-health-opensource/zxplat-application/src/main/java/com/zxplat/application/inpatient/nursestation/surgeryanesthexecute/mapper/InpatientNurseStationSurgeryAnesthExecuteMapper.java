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
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 手术/麻醉执行弹窗 Mapper：SR/人员名 → DialogDto；Submit → EXE-02 写槽；EXE 响应 → SAE 响应。
 *
 * <p>禁止承载业务门控或类目校验。</p>
 */
public interface InpatientNurseStationSurgeryAnesthExecuteMapper {

    /**
     * 组装执行弹窗 DTO（只读人员名由调用方预解析）。
     *
     * @param sr           服务申请列表模型（调用方保证非空）
     * @param planTime     计划槽位
     * @param activityName 项目名（可空）
     * @param nameById     医务人员主键→姓名
     * @return 弹窗 DTO；sr 为 null 时返回 null
     */
    SurgeryAnesthExecuteDialogDto toDialogDto(
        ServiceRequestListModel sr,
        LocalDateTime planTime,
        String activityName,
        Map<Long, String> nameById);

    /**
     * 提交请求 → EXE-02 单槽执行请求（含手术/麻醉弹窗标记与横表字段）。
     *
     * @param request 提交请求（调用方保证非空）
     * @return EXE-02 请求
     */
    ExecuteNurseStationOrdersRequest toExecuteOrdersRequest(SubmitSurgeryAnesthExecuteRequest request);

    /**
     * EXE-02 响应 → SAE-02 响应。
     *
     * @param executeResponse EXE 执行结果（可空）
     * @return SAE 响应；入参 null 时返回空成功壳
     */
    SubmitSurgeryAnesthExecuteResponse toSubmitResponse(ExecuteNurseStationOrdersResponse executeResponse);

    /**
     * 提交请求 → 写槽位（含 surgeryAnesthExecuteFlag）。
     *
     * @param request 提交请求
     * @return 写槽位 DTO
     */
    NurseStationOrderExecuteWriteSlotDto toWriteSlot(SubmitSurgeryAnesthExecuteRequest request);
}
