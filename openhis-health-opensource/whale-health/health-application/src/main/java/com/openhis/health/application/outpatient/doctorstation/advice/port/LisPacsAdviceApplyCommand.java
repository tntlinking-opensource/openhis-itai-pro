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

package com.openhis.health.application.outpatient.doctorstation.advice.port;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-DOR-15：检验/检查外发命令（目标态网关入参壳，字段随对接协议扩展）。
 */
@Getter
@Setter
public class LisPacsAdviceApplyCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 诊疗服务申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务申请主键")
    private Long requestId;

    /** 部位/样本明细摘要（BR-REQ-008）。 */
    @Schema(description = "服务申请明细摘要")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;
}
