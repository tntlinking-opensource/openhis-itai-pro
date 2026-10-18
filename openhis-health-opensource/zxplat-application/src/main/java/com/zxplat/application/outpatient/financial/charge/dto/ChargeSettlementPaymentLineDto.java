/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 已结算支付摘要下的收费明细行（支付记录弹窗只读对照；可含挂号费/已退费等收银台列表外项）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "支付关联收费明细行")
public class ChargeSettlementPaymentLineDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项主键")
    private Long chargeItemId;

    @Schema(description = "单据号")
    private String busNo;

    @Schema(description = "收费项目名称")
    private String itemName;

    @Schema(description = "数量展示")
    private String quantityDisplay;

    @Schema(description = "数量单位")
    private String quantityUnit;

    @Schema(description = "收费状态码")
    private String statusCode;

    @Schema(description = "收费状态文案")
    private String statusText;

    @Schema(description = "行金额")
    private BigDecimal totalPrice;
}
