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

package com.openhis.health.application.outpatient.doctorstation.refundapplication.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DRA-01：可退账单候选行（按支付分组展示）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-可退账单行")
public class RefundableChargeLineItemDto {

    /** 支付流水主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "支付锚点")
    private Long paymentId;

    /** 支付业务号。 */
    @Schema(description = "支付业务号展示")
    private String paymentBusNo;

    /** 支付状态持久化码。 */
    @Schema(description = "支付状态枚举码（PaymentStatus#getCode()）")
    private String paymentStatusEnum;

    /** 支付状态展示。 */
    @Schema(description = "支付状态展示文案")
    private String paymentStatusEnumText;

    /** 收费明细主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费行锚点")
    private Long chargeItemId;

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 上游业务表名。 */
    @Schema(description = "业务来源表名（与 adm_charge_item.service_table 一致）")
    private String serviceTable;

    /** 上游业务主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "业务来源主键")
    private Long serviceId;

    /** 收费明细状态持久化码。 */
    @Schema(description = "收费明细状态枚举码（ChargeItemStatus#getCode()）")
    private String chargeItemStatusEnum;

    /** 收费明细状态展示。 */
    @Schema(description = "收费明细状态展示文案")
    private String chargeItemStatusEnumText;

    /** 退费状态展示。 */
    @Schema(description = "收费项退费状态展示文案（BR-DRA-002）")
    private String refundStatusText;

    /** 收费项名称摘要。 */
    @Schema(description = "收费项名称摘要")
    private String itemDisplayName;

    /** 计价数量。 */
    @Schema(description = "计价数量")
    private BigDecimal quantity;

    /** 行金额。 */
    @Schema(description = "行金额（与收费明细总价口径一致）")
    private BigDecimal lineTotalAmount;

    /** 分组排序键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "用于前端稳定分组排序（典型取 paymentId）")
    private Long paymentGroupSortKey;
}
