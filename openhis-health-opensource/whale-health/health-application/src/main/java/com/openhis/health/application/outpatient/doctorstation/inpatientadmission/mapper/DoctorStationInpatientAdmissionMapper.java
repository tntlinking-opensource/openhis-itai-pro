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

package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.mapper;

import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.request.RequestInpatientAdmissionRequest;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response.CheckInpatientAdmissionExistsResponse;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response.RequestInpatientAdmissionResponse;
import com.openhis.health.domain.administration.encounter.command.RequestInpatientAdmissionCommand;
import com.openhis.health.domain.administration.encounter.queries.model.OpenInpatientLineQuery;

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
}
