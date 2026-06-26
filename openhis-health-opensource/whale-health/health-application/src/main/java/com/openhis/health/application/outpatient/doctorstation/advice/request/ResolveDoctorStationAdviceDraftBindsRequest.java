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

package com.openhis.health.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-23：暂存前绑定解析请求（与 IF-DOR-14 暂存变更行同构，纯读）。
 */
@Data
@Accessors(chain = true)
/** 医生站-医嘱开立-暂存前绑定解析请求。 */
@Schema(description = "医生站-医嘱开立-暂存前绑定解析请求")
public class ResolveDoctorStationAdviceDraftBindsRequest {

    /** 当前就诊上下文，与 IF-DOR-14 门控一致。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 必须与就诊患者一致，由 AppService 与领域二次校验。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 待暂存的变更行；服务端跳过 DELETE，对其余药品/诊疗行解析直接绑定（不含级联）。 */
    @NotEmpty(message = "暂存变更行不能为空")
    @Valid
    @Schema(description = "暂存变更行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DoctorStationAdviceDraftLineRequest> lines;
}
