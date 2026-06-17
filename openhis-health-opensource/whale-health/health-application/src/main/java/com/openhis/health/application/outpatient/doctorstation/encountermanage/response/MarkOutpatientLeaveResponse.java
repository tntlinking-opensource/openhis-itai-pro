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

package com.openhis.health.application.outpatient.doctorstation.encountermanage.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生站门诊暂离响应（IF-DOR-03）。
 */
@Data
@Accessors(chain = true)
/** 医生站-门诊暂离响应。 */
@Schema(description = "医生站-门诊暂离响应")
public class MarkOutpatientLeaveResponse {

    /** 就诊标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 是否成功。 */
    @Schema(description = "领域命令是否已成功执行")
    private Boolean success;
}
