/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 清算金额汇总与 3202 明细行预览查询条件。
 * <p>汇总按 {@code insutype + clrType} 分组返回列表；可选维度过滤收窄结果行。</p>
 */
@Data
@Accessors(chain = true)
public class SummarizeClearAmountCommand {

    /** 费用性质/契约业务号（签到与机构上下文；本地汇总可不强依赖）。 */
    private String contractBusNo;

    /** 契约主键（可选）。 */
    private Long contractId;

    /** 汇总开始日期 yyyy-MM-dd（含当日）。 */
    private String begndate;

    /** 汇总结束日期 yyyy-MM-dd（含当日）。 */
    private String enddate;

    /**
     * 可选：仅保留该险种分组行；空则不过滤险种。
     */
    private String insutype;

    /**
     * 可选：仅保留该清算类别分组行（如 11/21）；空则不过滤类别。
     */
    private String clrType;
}
