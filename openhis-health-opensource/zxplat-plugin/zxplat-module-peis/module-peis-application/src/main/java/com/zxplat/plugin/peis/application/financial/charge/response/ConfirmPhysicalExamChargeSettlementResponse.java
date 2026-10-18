/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 体检正式结算响应（IF-PE-CHG-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检正式结算响应")
public class ConfirmPhysicalExamChargeSettlementResponse {

    /** 支付流水主键。 */
    @Schema(description = "支付流水主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 发票草稿主键（零元不开票时为 null）。 */
    @Schema(description = "发票草稿主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 成功提示码。 */
    @Schema(description = "成功提示码")
    private String successMessageCode;
}
