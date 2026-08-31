/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.dto;

import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 护士耗材使用明细导出行（IF-RPT-NWL-06；与 {@link NurseConsumableDetailRowDto} 同构）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士耗材使用明细导出行")
public class NurseConsumableDetailExportRowDto {

    /** 护士耗材使用明细导出行。 */
    @Excel(name = "耗材名称", sort = 1)
    private String deviceName;

    /** 规格。 */
    @Excel(name = "规格", sort = 2)
    private String specText;

    /** 高/低值展示名。 */
    @Excel(name = "高/低值", sort = 3)
    private String hvcmDisplay;

    /** 生成来源展示名。 */
    @Excel(name = "生成来源", sort = 4)
    private String generateSourceDisplay;

    /** 护士姓名。 */
    @Excel(name = "执行护士", sort = 5)
    private String practitionerName;

    /** 业务时间展示。 */
    @Excel(name = "业务时间", sort = 6)
    private String businessTimeText;

    /** 数量（导出整数）。 */
    @Excel(name = "数量", sort = 7, scale = 0)
    private BigDecimal quantity;

    /** 单位展示名。 */
    @Excel(name = "单位", sort = 8)
    private String unitCodeLabel;

    /** 患者姓名。 */
    @Excel(name = "患者姓名", sort = 9)
    private String patientName;

    /** 就诊号。 */
    @Excel(name = "就诊号", sort = 10)
    private String encounterBusNo;
}
