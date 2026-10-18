/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 汇总领药单列表行（护士站已提交汇总单卡片；含申请/核发时间与行数摘要）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总领药单")
public class NurseStationDispenseSummarySheetDto {

    /** 汇总单号。 */
    @Schema(description = "汇总单号")
    private String summaryNo;

    /** 申请时间。 */
    @Schema(description = "申请时间")
    private String applyTime;

    /** 发药时间。 */
    @Schema(description = "发药时间")
    private String dispenseTime;

    /** 药房。 */
    @Schema(description = "药房")
    private String pharmacyName;

    /** 明细行数。 */
    @Schema(description = "明细行数")
    private Integer lineCount;

    /** 患者数。 */
    @Schema(description = "患者数")
    private Integer patientCount;

    /** 状态码。 */
    @Schema(description = "状态码")
    private String statusEnum;

    /** 状态文案。 */
    @Schema(description = "状态文案")
    private String statusEnumText;
}
