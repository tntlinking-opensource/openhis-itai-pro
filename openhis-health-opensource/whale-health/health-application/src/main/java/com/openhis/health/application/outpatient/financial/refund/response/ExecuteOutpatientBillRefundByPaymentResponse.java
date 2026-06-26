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

package com.openhis.health.application.outpatient.financial.refund.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按原支付执行门诊账单退费响应（IF-REF-05）。
 */
@Data
@Accessors(chain = true)
/** 按原支付执行门诊账单退费响应。 */
@Schema(description = "按原支付执行门诊账单退费响应")
public class ExecuteOutpatientBillRefundByPaymentResponse {

    @Schema(description = "是否执行成功")
    /** 是否执行成功（业务字段）。 */
    private boolean success;

    /** 逆向支付单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 逆向支付单主键。 */
    @Schema(description = "逆向支付单主键")
    /** 逆向支付单主键（业务字段）。 */
    private Long reversePaymentId;

    /** 原支付主单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键")
    /** 原支付主单主键（业务字段）。 */
    private Long originalPaymentId;

    /** 是否执行了半退再结算分支。 */
    @Schema(description = "是否执行了半退再结算分支")
    /** 是否执行了半退再结算分支（业务字段）。 */
    private boolean halfRefundExecuted;

    /** 医保正式结算是否已撤销（同步完成或无需撤销时为 true）。 */
    @Schema(description = "医保正式结算是否已撤销")
    /** 医保正式结算是否已撤销（业务字段）。 */
    private Boolean insuranceRevoked;
}
