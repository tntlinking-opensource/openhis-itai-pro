/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.inpatientadmission.mapper;

import com.zxplat.application.outpatient.doctorstation.inpatientadmission.request.RequestInpatientAdmissionRequest;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.response.CheckInpatientAdmissionExistsResponse;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.response.RequestInpatientAdmissionResponse;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.response.WithdrawInpatientAdmissionResponse;
import com.zxplat.domain.administration.encounter.command.RequestInpatientAdmissionCommand;
import com.zxplat.domain.administration.encounter.queries.model.OpenInpatientLineQuery;

/**
 * 门诊医生站住院申请：Request/Command/Response 字段级装配。
 *
 * <p>禁止承载业务流程判断；校验与门禁在 {@code DoctorStationInpatientAdmissionAppService}。</p>
 */
public interface DoctorStationInpatientAdmissionMapper {

    /**
     * IF-IP-REQ-01（Cmd 入参）：请求体 → 发起住院申请领域命令。
     *
     * @param request            应用层请求；调用方保证非空且已通过 Gate/Pre
     * @param resolvedPatientId  由源门诊就诊解析的患者主键
     * @return 单条 item 的批量命令；{@code request} 为 null 时返回 null
     */
    RequestInpatientAdmissionCommand toRequestInpatientAdmissionCommand(
        RequestInpatientAdmissionRequest request,
        Long resolvedPatientId
    );

    /**
     * IF-IP-REQ-01（Asm）：写侧结果 → 提交响应。
     *
     * @param encounterId 新建住院就诊主键
     * @param busNo       住院业务号
     * @param success     是否成功
     * @return 响应体
     */
    RequestInpatientAdmissionResponse toRequestInpatientAdmissionResponse(
        Long encounterId,
        String busNo,
        boolean success
    );

    /**
     * IF-IP-REQ-02（Asm）：只读查询模型 → 是否存在住院线响应。
     *
     * @param query 未取消住院线查询结果；调用方保证非 null
     * @return 响应体
     */
    CheckInpatientAdmissionExistsResponse toCheckInpatientAdmissionExistsResponse(OpenInpatientLineQuery query);

    /**
     * IF-IP-REQ-03（Asm）：撤回（软删）结果 → 响应体。
     *
     * @param inpatientEncounterId 被撤回的住院就诊主键
     * @param statusEnum           撤回前状态码（通常为 PLANNED）
     * @param success              是否成功
     * @return 响应体
     */
    WithdrawInpatientAdmissionResponse toWithdrawInpatientAdmissionResponse(
        Long inpatientEncounterId,
        String statusEnum,
        boolean success
    );
}
