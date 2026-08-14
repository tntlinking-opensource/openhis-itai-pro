/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.inpatient.dto;

import java.math.BigDecimal;

import com.openhis.whale.core.application.annotation.Excel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院耗材使用报表导出行（IF-RPT-ICU-04；汇总/明细共用列，空列按 Tab 留空）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院耗材使用报表导出行")
public class InpatientConsumableUsageExportRowDto {

    /** 来源展示。 */
    @Excel(name = "来源", sort = 1)
    private String sourceText;

    /** 业务时间。 */
    @Excel(name = "业务时间", sort = 2)
    private String businessTimeText;

    /** 患者。 */
    @Excel(name = "患者", sort = 3)
    private String patientName;

    /** 住院号。 */
    @Excel(name = "住院号", sort = 4)
    private String encounterBusNo;

    /** 费用性质展示名。 */
    @Excel(name = "费用性质", sort = 5)
    private String feeNatureDisplay;

    /** 医疗类别展示文案。 */
    @Excel(name = "医疗类别", sort = 6)
    private String medTypeDisplay;

    /** 病区。 */
    @Excel(name = "病区", sort = 7)
    private String wardName;

    /** 床号。 */
    @Excel(name = "床号", sort = 8)
    private String bedName;

    /** 科室。 */
    @Excel(name = "科室", sort = 9)
    private String organizationName;

    /** 库位。 */
    @Excel(name = "库位", sort = 10)
    private String locationName;

    /** 耗材名称。 */
    @Excel(name = "耗材名称", sort = 11)
    private String deviceName;

    /** 规格。 */
    @Excel(name = "规格", sort = 12)
    private String specText;

    /** 数量。 */
    @Excel(name = "数量", sort = 13)
    private BigDecimal quantity;

    /** 单位。 */
    @Excel(name = "单位", sort = 14)
    private String unitText;

    /** 金额（售价别名）。 */
    @Excel(name = "金额", sort = 15)
    private BigDecimal amount;

    /** 进价单价。 */
    @Excel(name = "进价单价", sort = 16)
    private BigDecimal purchaseUnitPrice;

    /** 进价总额。 */
    @Excel(name = "进价总额", sort = 17)
    private BigDecimal purchaseAmount;

    /** 售价单价。 */
    @Excel(name = "售价单价", sort = 18)
    private BigDecimal saleUnitPrice;

    /** 售价总额。 */
    @Excel(name = "售价总额", sort = 19)
    private BigDecimal saleAmount;

    /** 批号。 */
    @Excel(name = "批号", sort = 20)
    private String lotNumber;

    /** 状态/类型。 */
    @Excel(name = "状态/类型", sort = 21)
    private String statusOrTypeText;

    /** 执行人（发耗材人）。 */
    @Excel(name = "执行人", sort = 22)
    private String performerName;
}
