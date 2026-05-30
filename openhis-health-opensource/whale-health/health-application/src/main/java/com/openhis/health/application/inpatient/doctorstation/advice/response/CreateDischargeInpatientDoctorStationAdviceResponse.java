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

package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-21：开立出院医嘱响应（出院服务申请与离院里程碑同事务写入后的回传）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-开立出院医嘱响应")
public class CreateDischargeInpatientDoctorStationAdviceResponse {

    /** 新创建的出院类 {@code ServiceRequest} 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "出院 ServiceRequest 主键")
    private Long serviceRequestId;

    /** 同事务写入的 {@code EncounterHistory} 离院里程碑记录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "离院里程碑主键")
    private Long encounterHistoryId;

    /** 整单事务是否成功完成。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 面向前端展示的结果说明或错误提示。 */
    @Schema(description = "提示文案")
    private String message;
}
