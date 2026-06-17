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

package com.openhis.health.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 已结算单查阅请求（IF-IP-STL-07）。
 */
@Data
@Accessors(chain = true)
/** 已结算单查阅请求。 */
@Schema(description = "已结算单查阅请求", requiredProperties = {"encounterId", "patientId"})
public class LoadSettledInpatientPaymentViewRequest {

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;

    /** 指定结算单 ID；缺省取最近成功住院临床结算单。 */
    @Schema(description = "指定结算单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 指定结算单 ID（业务字段）。 */
    private Long paymentId;

    /** 费用呈现模式：DETAIL / SUMMARY。 */
    @Schema(description = "费用呈现模式 code")
    /** 费用呈现模式 code（业务字段）。 */
    private String presentationMode;
}
