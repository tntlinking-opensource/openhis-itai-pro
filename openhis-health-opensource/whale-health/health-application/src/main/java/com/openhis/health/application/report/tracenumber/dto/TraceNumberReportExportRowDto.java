/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.dto;

import com.openhis.whale.core.application.annotation.Excel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * IF-TRC-RPT-05 导出 Excel 行（列与列表对客列同构）。
 */
@Getter
@Setter
public class TraceNumberReportExportRowDto {

    /** 追溯码。 */
    @Excel(name = "追溯码", sort = 1)
    private String traceNo;

    /** 药品编码。 */
    @Excel(name = "药品编码", sort = 2)
    private String medicationCode;

    /** 药品名称。 */
    @Excel(name = "药品名称", sort = 3)
    private String medicationName;

    /** 批号。 */
    @Excel(name = "批号", sort = 4)
    private String lotNumber;

    /** 在院状态。 */
    @Excel(name = "在院状态", sort = 5)
    private String inHospitalStatusLabel;

    /** 拆零剩余量。 */
    @Excel(name = "拆零剩余量", sort = 6, scale = 0)
    private BigDecimal remainingSplitQuantity;

    /** 追溯码单位。 */
    @Excel(name = "追溯码单位", sort = 7)
    private String traceUnitCodeLabel;

    /** 本库状态。 */
    @Excel(name = "本库状态", sort = 8)
    private String locationStockStatusLabel;

    /** 本库净拆零量。 */
    @Excel(name = "本库净拆零量", sort = 9, scale = 0)
    private BigDecimal locationNetSplitQuantity;

    /** 最近流水时间（格式化展示）。 */
    @Excel(name = "最近流水时间", sort = 10)
    private String lastFlowAtLabel;
}
