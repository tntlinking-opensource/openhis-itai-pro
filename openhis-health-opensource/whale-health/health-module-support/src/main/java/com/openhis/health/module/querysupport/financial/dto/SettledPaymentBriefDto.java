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

package com.openhis.health.module.querysupport.financial.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已结算支付单摘要 DTO（IF-IP-STL-07；AS-IP-FIN-004 读侧增量）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "已结算支付单摘要")
public class SettledPaymentBriefDto {

    /** 支付流水 ID。 */
    @Schema(description = "支付流水 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 结算/反结账单时间。 */
    @Schema(description = "结算时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime settleTime;

    /** 应收合计（正向展示绝对值，不含符号）。 */
    @Schema(description = "应收合计", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal receivableTotal;

    /** 支付方式展示文案。 */
    @Schema(description = "支付方式展示")
    private String payMethodText;

    /** 历史条类型：FORWARD 正向结单 / REVERSE 逆向反结单。 */
    @Schema(description = "历史条类型：FORWARD/REVERSE")
    private String recordType;

    /** 带符号展示金额：正向为正、逆向为负。 */
    @Schema(description = "带符号展示金额")
    private BigDecimal signedAmount;

    /** 支付流水状态 code（SUCCESS/REFUNDED 等）。 */
    @Schema(description = "支付流水状态 code")
    private String statusEnum;

    /** 逆向单指向的原单 ID；正向为空。 */
    @Schema(description = "逆向单关联的原单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relationId;

    /** 是否可在结算页点击反结。 */
    @Schema(description = "是否可反结")
    private Boolean reverseEnabled;

    /** reverseEnabled=false 时的前端 Tooltip 文案。 */
    @Schema(description = "不可反结原因")
    private String reverseDisabledReason;
}
