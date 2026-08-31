/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 体检收银员执行退费响应（IF-PE-CHG-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检收银员执行退费响应")
public class RefundPhysicalExamChargesResponse {

    /** 是否执行成功。 */
    @Schema(description = "是否执行成功")
    private Boolean success;

    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;

    /** 逆向支付单主键。 */
    @Schema(description = "逆向支付单主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reversePaymentId;
}
