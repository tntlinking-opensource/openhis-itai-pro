/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.feedetail.dto;

import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊费用明细导出行 DTO（IF-OP-FDT-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细导出行")
public class OutpatientFeeDetailExportRowDto {

    /** 患者姓名。 */
    @Excel(name = "姓名", sort = 1)
    private String patientName;

    /** 门诊号。 */
    @Excel(name = "门诊号", sort = 2)
    private String busNo;

    /** 挂号科室。 */
    @Excel(name = "挂号科室", sort = 3)
    private String registrationDepartmentName;

    /** 费用大类。 */
    @Excel(name = "费用大类", sort = 4)
    private String feeCategoryDisplay;

    /** 细分类。 */
    @Excel(name = "细分类", sort = 5)
    private String subCategoryDisplay;

    /** 项目名称。 */
    @Excel(name = "项目名称", sort = 6)
    private String productName;

    /** 项目编码。 */
    @Excel(name = "项目编码", sort = 7)
    private String productCode;

    /** 规格。 */
    @Excel(name = "规格", sort = 8)
    private String specification;

    /** 数量。 */
    @Excel(name = "数量", sort = 9)
    private BigDecimal quantityValue;

    /** 单位。 */
    @Excel(name = "单位", sort = 10)
    private String quantityUnitDisplay;

    /** 开单医生。 */
    @Excel(name = "开单医生", sort = 11)
    private String orderingDoctorName;

    /** 收费人。 */
    @Excel(name = "收费人", sort = 12)
    private String cashierName;

    /** 结算时间。 */
    @Excel(name = "结算时间", sort = 13)
    private String billDate;

    /** 医保等级。 */
    @Excel(name = "医保等级", sort = 14)
    private String insuranceLevelDisplay;

    /** 医保类型。 */
    @Excel(name = "医保类型", sort = 15)
    private String insuranceTypeDisplay;

    /** 原价。 */
    @Excel(name = "原价", sort = 16)
    private BigDecimal baseAmount;

    /** 优惠金额。 */
    @Excel(name = "优惠金额", sort = 17)
    private BigDecimal discountAmount;

    /** 应收金额。 */
    @Excel(name = "应收金额", sort = 18)
    private BigDecimal receivableAmount;

    /** 折后金额。 */
    @Excel(name = "折后金额", sort = 19)
    private BigDecimal discountedAmount;

    /** 单价。 */
    @Excel(name = "单价", sort = 20)
    private BigDecimal unitPrice;

    /** 行金额。 */
    @Excel(name = "行金额", sort = 21)
    private BigDecimal lineAmount;

    /** 实收金额。 */
    @Excel(name = "实收金额", sort = 22)
    private BigDecimal actualReceivedAmount;

    /** 医保金额。 */
    @Excel(name = "医保金额", sort = 23)
    private BigDecimal insuranceAmount;

    /** 自费金额。 */
    @Excel(name = "自费金额", sort = 24)
    private BigDecimal selfPayAmount;
}
