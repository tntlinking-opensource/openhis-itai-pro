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

package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-01：发起住院申请响应。
 */
@Data
@Accessors(chain = true)
/** 门诊医生站-住院申请-发起住院申请响应。 */
@Schema(description = "门诊医生站-住院申请-发起住院申请响应")
public class RequestInpatientAdmissionResponse {

    /** 新建住院就诊标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "新建住院就诊主键")
    private Long inpatientEncounterId;

    /** 住院业务号（独立前缀，如 ZY）。 */
    @Schema(description = "住院就诊业务号")
    private String inpatientEncounterBusNo;

    /** 是否成功。 */
    @Schema(description = "申请是否成功")
    private boolean success;
}
