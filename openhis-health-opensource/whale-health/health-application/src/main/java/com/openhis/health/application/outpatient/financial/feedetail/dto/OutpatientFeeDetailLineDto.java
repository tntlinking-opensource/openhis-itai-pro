/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.feedetail.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊费用明细行 DTO（IF-OP-FDT-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细行")
public class OutpatientFeeDetailLineDto {

    /** 收费明细主键。 */
    @Schema(description = "收费明细主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    /** 关联 payment 主键。 */
    @Schema(description = "支付流水主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 费用大类 code。 */
    @Schema(description = "费用大类 code")
    private String feeCategoryCode;

    /** 费用大类展示文案。 */
    @Schema(description = "费用大类")
    private String feeCategoryDisplay;

    /** 细分类 code（fin_type_code，定价行财务类型）。 */
    @Schema(description = "细分类 code（fin_type_code）")
    private String subCategoryCode;

    /** 细分类展示文案（财务类型字典 label）。 */
    @Schema(description = "细分类（财务类型）")
    private String subCategoryDisplay;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String productName;

    /** 院内项目编码。 */
    @Schema(description = "项目编码")
    private String productCode;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantityValue;

    /** 数量单位展示。 */
    @Schema(description = "单位")
    private String quantityUnitDisplay;

    /** 开单医生主键。 */
    @Schema(description = "开单医生主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingDoctorId;

    /** 开单医生姓名。 */
    @Schema(description = "开单医生")
    private String orderingDoctorName;

    /** 收费人主键。 */
    @Schema(description = "收费人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cashierId;

    /** 收费人姓名。 */
    @Schema(description = "收费人")
    private String cashierName;

    /** 结算时间展示（payment.bill_date，{@code yyyy-MM-dd HH:mm:ss}）。 */
    @Schema(description = "结算时间")
    private String billDate;

    /** 收退方向：CHARGE / REFUND。 */
    @Schema(description = "收退方向")
    private String tradeDirection;

    /** 医保等级展示。 */
    @Schema(description = "医保等级")
    private String insuranceLevelDisplay;

    /** 医保类型展示。 */
    @Schema(description = "医保类型")
    private String insuranceTypeDisplay;

    /** 原价。 */
    @Schema(description = "原价")
    private BigDecimal baseAmount;

    /** 优惠金额。 */
    @Schema(description = "优惠金额")
    private BigDecimal discountAmount;

    /** 应收金额。 */
    @Schema(description = "应收金额")
    private BigDecimal receivableAmount;

    /** 折后金额。 */
    @Schema(description = "折后金额")
    private BigDecimal discountedAmount;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 行金额（退单为负）。 */
    @Schema(description = "行金额")
    private BigDecimal lineAmount;

    /** 实收金额（退单为负）。 */
    @Schema(description = "实收金额")
    private BigDecimal actualReceivedAmount;

    /** 医保金额（退单为负）。 */
    @Schema(description = "医保金额")
    private BigDecimal insuranceAmount;

    /** 自费金额（退单为负）。 */
    @Schema(description = "自费金额")
    private BigDecimal selfPayAmount;

    /** 折扣率展示（可选列）。 */
    @Schema(description = "折扣率展示")
    private String discountRateDisplay;
}
