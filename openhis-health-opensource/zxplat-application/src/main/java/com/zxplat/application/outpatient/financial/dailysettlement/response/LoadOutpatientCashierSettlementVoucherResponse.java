/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.dailysettlement.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.financial.cashiersettlement.CashierSettlementMountedVoucherDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊交账原单打印响应（按挂载 payment 现算）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊交账原单打印响应（挂载现算）")
public class LoadOutpatientCashierSettlementVoucherResponse {

    /** 交账头主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头主键")
    private Long settlementId;

    /** 交账单号。 */
    @Schema(description = "交账单号")
    private String busNo;

    /** 按挂载 payment 现算的打印体。 */
    @Schema(description = "按挂载 payment 现算的打印体")
    private CashierSettlementMountedVoucherDto mountedVoucher;
}
