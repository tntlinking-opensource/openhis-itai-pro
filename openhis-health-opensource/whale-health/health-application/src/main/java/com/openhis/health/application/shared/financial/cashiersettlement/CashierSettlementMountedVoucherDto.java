/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.cashiersettlement;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 交账原单打印体（按挂载 payment 现算；不落库）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "交账原单打印体（挂载现算）")
public class CashierSettlementMountedVoucherDto {

    /** 结构版本。 */
    @Schema(description = "结构版本")
    private Integer schemaVersion = 1;

    /** 收款人展示名。 */
    @Schema(description = "收款人展示名")
    private String cashierName;

    /** 日结时间文案。 */
    @Schema(description = "日结时间文案")
    private String settlementPeriodText;

    /**
     * 上半区 fin_type 行（交账批次口径）。
     *
     * <p>运行时为日结 ChannelRow DTO 列表。</p>
     */
    @Schema(description = "上半区 fin_type 行")
    private Object rows;

    /**
     * 下半区汇总（交账批次口径）。
     *
     * <p>运行时为日结 Summary DTO。</p>
     */
    @Schema(description = "下半区汇总")
    private Object summary;

    /** 本次挂载 payment 主键（string 保精度）。 */
    @Schema(description = "本次挂载 payment 主键（string 保精度）")
    private List<String> paymentIds;
}
