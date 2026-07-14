/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto;

import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院发退药记录导出行 DTO（IF-IPR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发退药记录导出行")
public class InpatientPharmacyDispenseRecordExportRowDto {

    /** 记录类型展示文本。 */
    @Excel(name = "记录类型", sort = 1)
    private String recordTypeText;

    /** 发药时间。 */
    @Excel(name = "发药时间", sort = 2)
    private String dispenseTime;

    /** 药房名称。 */
    @Excel(name = "药房", sort = 3)
    private String pharmacyLocationName;

    /** 发药人姓名。 */
    @Excel(name = "发药人", sort = 4)
    private String dispenserName;

    /** 患者姓名。 */
    @Excel(name = "患者", sort = 5)
    private String patientName;

    /** 住院号。 */
    @Excel(name = "住院号", sort = 6)
    private String inpatientNo;

    /** 病区名称。 */
    @Excel(name = "病区", sort = 7)
    private String wardName;

    /** 床号。 */
    @Excel(name = "床号", sort = 8)
    private String bedName;

    /** 处方号。 */
    @Excel(name = "处方号", sort = 9)
    private String prescriptionNo;

    /** 药品编码。 */
    @Excel(name = "药品编码", sort = 10)
    private String medicationCode;

    /** 药品名称。 */
    @Excel(name = "药品名称", sort = 11)
    private String medicationName;

    /** 规格展示。 */
    @Excel(name = "规格", sort = 12)
    private String medicationSpec;

    /** 数量（绝对值）。 */
    @Excel(name = "数量", sort = 13)
    private BigDecimal quantity;

    /** 单位展示。 */
    @Excel(name = "单位", sort = 14)
    private String unitText;

    /** 单价。 */
    @Excel(name = "单价", sort = 15)
    private BigDecimal unitPrice;

    /** 金额（退药为负）。 */
    @Excel(name = "金额", sort = 16)
    private BigDecimal totalAmount;

    /** 批号。 */
    @Excel(name = "批号", sort = 17)
    private String lotNumber;

    /** 追溯码。 */
    @Excel(name = "追溯码", sort = 18)
    private String traceNo;

    /** 开单科室名称。 */
    @Excel(name = "开单科室", sort = 19)
    private String orderingDepartmentName;

    /** 用法用量摘要。 */
    @Excel(name = "用法用量", sort = 20)
    private String usageText;

    /** 汇总单号。 */
    @Excel(name = "汇总单号", sort = 21)
    private String summaryNo;
}
