/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.dto;

import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 科室/医生工作量明细导出行 DTO（IF-RPT-WKL-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室医生工作量明细导出行")
public class PractitionerWorkloadExportRowDto {

    /** 开单科室。 */
    @Excel(name = "开单科室", sort = 1)
    @Schema(description = "开单科室")
    private String requestingOrgName;

    /** 目录类型。 */
    @Excel(name = "目录类型", sort = 2)
    @Schema(description = "目录类型")
    private String catalogTypeDisplay;

    /** 项目名称。 */
    @Excel(name = "项目名称", sort = 3)
    @Schema(description = "项目名称")
    private String productName;

    /** 项目编码。 */
    @Excel(name = "项目编码", sort = 4)
    @Schema(description = "项目编码")
    private String productCode;

    /** 规格。 */
    @Excel(name = "规格", sort = 5)
    @Schema(description = "规格")
    private String specification;

    /** 开单医生。 */
    @Excel(name = "开单医生", sort = 6)
    @Schema(description = "开单医生")
    private String practitionerName;

    /** 开立时间。 */
    @Excel(name = "开立时间", sort = 7)
    @Schema(description = "开立时间")
    private String enteredDate;

    /** 应收。 */
    @Excel(name = "应收", sort = 8)
    @Schema(description = "应收")
    private BigDecimal receivableAmount;

    /** 实收。 */
    @Excel(name = "实收", sort = 9)
    @Schema(description = "实收")
    private BigDecimal actualReceivedAmount;

    /** 成本。 */
    @Excel(name = "成本", sort = 10)
    @Schema(description = "成本")
    private BigDecimal costAmount;
}
