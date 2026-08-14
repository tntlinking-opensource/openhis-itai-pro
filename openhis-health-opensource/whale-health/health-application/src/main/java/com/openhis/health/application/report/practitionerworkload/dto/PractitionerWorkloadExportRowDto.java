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
 * 科室/医生工作量明细导出行 DTO（IF-RPT-WKL-04；DD-报表中心-进售价口径）。
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

    /** 售价。 */
    @Excel(name = "售价", sort = 8)
    @Schema(description = "售价")
    private BigDecimal saleAmount;

    /** 进价。 */
    @Excel(name = "进价", sort = 9)
    @Schema(description = "进价")
    private BigDecimal purchaseAmount;
}
