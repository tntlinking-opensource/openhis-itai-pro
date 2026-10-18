/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊患者费用清单明细行（与 op-feeList-print 模板 {@code feeListRows} 列 field 一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊患者费用清单明细行")
public class ChargeFeeListRowDto {

    /** 就诊号（门诊业务号）。 */
    @Schema(description = "就诊号")
    private String encounterBusNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 就诊科室。 */
    @Schema(description = "就诊科室")
    private String departmentName;

    /** 开单医生。 */
    @Schema(description = "开单医生")
    private String orderingDoctorName;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 单价展示。 */
    @Schema(description = "单价")
    private String unitPriceText;

    /** 数量展示（含单位，如 1.00盒）。 */
    @Schema(description = "数量")
    private String quantityText;

    /** 行总金额展示。 */
    @Schema(description = "总金额")
    private String amountText;

    /** 医保等级展示（如 1甲 / 2乙 / 1自费）。 */
    @Schema(description = "医保等级")
    private String insuranceLevelText;

    /** 就诊类型展示（普通门诊 / 门诊慢病等）。 */
    @Schema(description = "就诊类型")
    private String visitTypeText;

    /** 项目类型展示（西药 / 治疗 / 医用耗材等）。 */
    @Schema(description = "项目类型")
    private String itemTypeText;

    /** 费用发生时间展示。 */
    @Schema(description = "时间")
    private String timeText;
}
