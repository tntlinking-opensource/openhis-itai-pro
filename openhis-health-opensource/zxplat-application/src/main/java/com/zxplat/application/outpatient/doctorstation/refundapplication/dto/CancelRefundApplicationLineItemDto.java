/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.refundapplication.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DRA-04：撤销退费提交体单行（支付锚点 + 收费行锚点；须覆盖整笔支付下全部退款中行）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-撤销退费-提交行")
public class CancelRefundApplicationLineItemDto {

    /** 支付锚点（与 IF-DRA-01 返回成套）。 */
    @NotNull(message = "支付流水标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "支付锚点（与 IF-DRA-01 返回成套）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long paymentId;

    /** 收费行锚点（须为退款中）。 */
    @NotNull(message = "收费明细标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费行锚点（须为退款中）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long chargeItemId;
}
