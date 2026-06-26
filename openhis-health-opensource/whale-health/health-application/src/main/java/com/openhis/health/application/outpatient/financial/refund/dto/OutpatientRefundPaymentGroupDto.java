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

package com.openhis.health.application.outpatient.financial.refund.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按原支付分组的待执行退费明细（IF-REF-03）。
 */
@Data
@Accessors(chain = true)
/** 按原支付分组的待执行退费明细。 */
@Schema(description = "按原支付分组的待执行退费明细")
public class OutpatientRefundPaymentGroupDto {

    /** 原支付主单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "原支付主单主键")
    /** 原支付主单主键（业务字段）。 */
    private Long paymentId;

    /** 支付业务号。 */
    @Schema(description = "支付业务号")
    /** 支付业务号（业务字段）。 */
    private String paymentBusNo;

    /** 支付状态 code。 */
    @Schema(description = "支付状态编码")
    /** 支付状态编码（业务字段）。 */
    private String paymentStatusEnum;

    /** 支付状态展示文案。 */
    @Schema(description = "支付状态展示文案")
    /** 支付状态展示文案（业务字段）。 */
    private String paymentStatusEnumText;

    /** 原支付录入人（收费员）系统用户主键，对应 fin_payment_reconciliation.enterer_id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原支付收款人用户主键。 */
    @Schema(description = "原支付收款人用户主键")
    /** 原支付收款人用户主键（业务字段）。 */
    private Long entererUserId;

    /** 原支付收款人姓名（经医务人员主档解析，无法关联时为空）。 */
    @Schema(description = "原支付收款人姓名")
    /** 原支付收款人姓名（业务字段）。 */
    private String collectorName;

    /** 同支付下既有拟退又有保留时为半退。 */
    @Schema(description = "是否产品半退")
    /** 是否产品半退（业务字段）。 */
    private boolean halfRefundOnPayment;

    /** 拟退 REFUNDING 行。 */
    @Schema(description = "拟退行列表")
    /** 拟退行列表（业务字段）。 */
    private List<OutpatientRefundLineItemDto> refundingLines;

    /** 保留再生 PLANNED 行。 */
    @Schema(description = "保留再生行列表")
    /** 保留再生行列表（业务字段）。 */
    private List<OutpatientRefundLineItemDto> preservedLines;

    /** 应退展示金额。 */
    @Schema(description = "应退展示金额")
    /** 应退展示金额（业务字段）。 */
    private BigDecimal refundableAmount;

    /**
     * 原支付落库渠道码（{@code fin_payment_rec_detail.pay_enum} 去重）；
     * 供前端判定是否可能银联原路退，现金等仅含 {@code CASH} 时不查 BPC。
     */
    @Schema(description = "原支付落库渠道码列表")
    private List<String> originalPayChannelCodes;
}
