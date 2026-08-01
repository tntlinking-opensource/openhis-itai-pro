/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.dto;

import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊发药报表导出行 DTO（明细/汇总共用列头子集）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发药报表导出行")
public class OutpatientMedicationDispenseExportRowDto {

    /** 来源展示。 */
    @Excel(name = "来源", sort = 1)
    private String sourceText;

    /** 记录类型展示。 */
    @Excel(name = "类型", sort = 2)
    private String recordTypeText;

    /** 业务时间或维度名称。 */
    @Excel(name = "时间/维度", sort = 3)
    private String businessTimeOrDimension;

    /** 药房名称。 */
    @Excel(name = "药房", sort = 4)
    private String pharmacyLocationName;

    /** 患者或科室名称。 */
    @Excel(name = "患者/科室", sort = 5)
    private String patientOrDepartmentName;

    /** 门诊号。 */
    @Excel(name = "门诊号", sort = 6)
    private String encounterNo;

    /** 费用性质展示名。 */
    @Excel(name = "费用性质", sort = 7)
    private String feeNatureDisplay;

    /** 医疗类别展示文案。 */
    @Excel(name = "医疗类别", sort = 8)
    private String medTypeDisplay;

    /** 药品编码。 */
    @Excel(name = "药品编码", sort = 9)
    private String medicationCode;

    /** 药品名称。 */
    @Excel(name = "药品名称", sort = 10)
    private String medicationName;

    /** 规格。 */
    @Excel(name = "规格", sort = 11)
    private String medicationSpec;

    /** 数量/净数量。 */
    @Excel(name = "数量", sort = 12)
    private BigDecimal quantity;

    /** 单位。 */
    @Excel(name = "单位", sort = 13)
    private String unitText;

    /** 单价（售价别名）。 */
    @Excel(name = "单价", sort = 14)
    private BigDecimal unitPrice;

    /** 金额（售价别名）。 */
    @Excel(name = "金额", sort = 15)
    private BigDecimal totalAmount;

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

    /** 单号。 */
    @Excel(name = "单号", sort = 21)
    private String busNo;
}
