/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.dto;

import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 护士诊疗执行明细导出行（IF-RPT-NWL-06；与 {@link NurseExecutionDetailRowDto} 同构）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士诊疗执行明细导出行")
public class NurseExecutionDetailExportRowDto {

    /** 护士诊疗执行明细导出行。 */
    @Excel(name = "项目名称", sort = 1)
    private String itemName;

    /** 诊疗类目展示名。 */
    @Excel(name = "诊疗类目", sort = 2)
    private String categoryDisplay;

    /** 生成来源展示名。 */
    @Excel(name = "生成来源", sort = 3)
    private String generateSourceDisplay;

    /** 执行护士姓名。 */
    @Excel(name = "执行护士", sort = 4)
    private String practitionerName;

    /** 执行时间展示。 */
    @Excel(name = "执行时间", sort = 5)
    private String recordedTimeText;

    /** 患者姓名。 */
    @Excel(name = "患者姓名", sort = 6)
    private String patientName;

    /** 就诊号。 */
    @Excel(name = "就诊号", sort = 7)
    private String encounterBusNo;

    /** 售价。 */
    @Excel(name = "售价", sort = 8, scale = 2)
    @Schema(description = "售价")
    private BigDecimal saleAmount;

    /** 优惠金额。 */
    @Excel(name = "优惠金额", sort = 9, scale = 2)
    @Schema(description = "优惠金额")
    private BigDecimal discountAmount;

    /** 实收金额。 */
    @Excel(name = "实收金额", sort = 10, scale = 2)
    @Schema(description = "实收金额")
    private BigDecimal actualReceivedAmount;
}
