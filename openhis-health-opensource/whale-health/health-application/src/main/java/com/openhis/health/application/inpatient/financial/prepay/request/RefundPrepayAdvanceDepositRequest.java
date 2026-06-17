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

package com.openhis.health.application.inpatient.financial.prepay.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayPaymentDetailDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退预交金请求（IF-IP-PREP-04）。
 */
@Data
@Accessors(chain = true)
/** 退预交金请求。 */
@Schema(description = "退预交金请求")
public class RefundPrepayAdvanceDepositRequest {

    @NotNull(message = "住院就诊 ID 不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @NotNull(message = "患者 ID 不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @NotNull(message = "退费金额不能为空")
    /** 退费金额。 */
    @Schema(description = "退费金额，须大于 0 且不超过账户余额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal refundAmount;

    @NotNull(message = "退现明细不能为空")
    /** 退现渠道明细。 */
    @Schema(description = "退现渠道明细，至少一条", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PrepayPaymentDetailDto> paymentDetails;

    /** 操作员 ID；缺省取当前登录用户。 */
    @Schema(description = "操作员 ID；缺省取当前登录用户")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
