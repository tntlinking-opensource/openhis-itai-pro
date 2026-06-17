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

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-39：查询患者历史中药处方请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询患者历史中药处方请求")
public class ListPatientTcmRxHistoryRequest {

    /** 患者主键。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 当前就诊主键（排除）。 */
    @NotNull(message = "当前就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long currentEncounterId;

    /** 关键字（药品名/处方号）。 */
    @Schema(description = "关键字")
    private String keyword;
}
