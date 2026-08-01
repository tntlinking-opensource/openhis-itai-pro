/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.medicationrevenue;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 日结药品 TAB：收费侧药品明细行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结药品收费明细行")
public class DailySettlementMedicationChargeLineDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "payment 主键")
    private Long paymentId;

    @Schema(description = "支付单号")
    private String paymentNo;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费明细主键")
    private Long chargeItemId;

    @Schema(description = "项目名称")
    private String itemName;

    @Schema(description = "财务类别码")
    private String finTypeCode;

    @Schema(description = "财务类别名")
    private String finTypeName;

    @Schema(description = "数量")
    private BigDecimal quantity;

    @Schema(description = "金额（退费取负）")
    private BigDecimal amount;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药主键")
    private Long dispenseId;

    @Schema(description = "发药状态码")
    private String dispenseStatus;

    @Schema(description = "发药状态展示")
    private String dispenseStatusDisplay;

    @Schema(description = "发药时间")
    private LocalDateTime dispenseTime;

    @Schema(description = "收费时间")
    private LocalDateTime billDate;

    @Schema(description = "是否已收费未发药")
    private Boolean undispensed;
}
