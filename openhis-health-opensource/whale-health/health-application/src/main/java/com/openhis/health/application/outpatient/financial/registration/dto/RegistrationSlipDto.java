/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 挂号单详情（列表/打印共用；含 op-reg-print 模板 PrintData 字段）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号单详情")
public class RegistrationSlipDto {

    /** 挂号业务单号（slip bus_no）。 */
    @Schema(description = "挂号单号")
    private String slipNo;

    /** 就诊主键 {@code adm_encounter.id}。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 患者姓名（读模型展示）。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 挂号科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 挂号医师姓名。 */
    @Schema(description = "医生姓名")
    private String doctorName;

    /** 挂号类型/号别展示名。 */
    @Schema(description = "挂号类型")
    private String serviceTypeName;

    /** 就诊/挂号状态中文展示。 */
    @Schema(description = "状态文本")
    private String statusText;

    /** 挂号应收金额合计。 */
    @Schema(description = "金额")
    private BigDecimal amount;

    /** 小票累计打印次数。 */
    @Schema(description = "打印次数")
    private Integer printCount;

    // ---------- 挂号小票 PrintData（与 op-reg-print 模板 field 一致） ----------

    /** 机构全称（小票抬头 hospitalName）。 */
    @Schema(description = "机构名称")
    private String hospitalName;

    /** 性别中文展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄展示文案。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 费用性质/合同展示。 */
    @Schema(description = "费用性质")
    private String feeNatureText;

    /** 挂号科室名称（小票 registerDepartmentName）。 */
    @Schema(description = "挂号科室")
    private String registerDepartmentName;

    /** 挂号收费项目明细行。 */
    @Schema(description = "挂号收费项目行")
    private List<RegistrationReceiptChargeLineDto> chargeItemLines;

    /** 合计金额格式化文案。 */
    @Schema(description = "合计金额展示")
    private String totalAmountText;

    /** 统筹支付格式化文案（医保分解）。 */
    @Schema(description = "统筹支付展示")
    private String poolingPayText;

    /** 基金支付格式化文案。 */
    @Schema(description = "基金支付展示")
    private String fundPayText;

    /** 个人账户支付格式化文案。 */
    @Schema(description = "个人账户支付展示")
    private String accountPayText;

    /** 起付线格式化文案。 */
    @Schema(description = "起付线展示")
    private String deductibleLineText;

    /**
     * 公务员待遇展示（2207 {@code cvlserv_flag}：是/否；无回执时为空串）。
     *
     * <p>仅小票展示，不参与金额计算。</p>
     */
    @Schema(description = "公务员待遇展示")
    private String cvlservFlagText;

    /**
     * 公务员医疗补助资金支出展示（2207 {@code cvlserv_pay}）。
     *
     * <p>仅小票展示，不参与金额计算。</p>
     */
    @Schema(description = "公务员补助展示")
    private String cvlservPayText;

    /** 现金渠道支付格式化文案。 */
    @Schema(description = "现金支付展示")
    private String cashPayText;

    /** 微信渠道支付格式化文案。 */
    @Schema(description = "微信支付展示")
    private String wechatPayText;

    /** 支付宝渠道支付格式化文案。 */
    @Schema(description = "支付宝支付展示")
    private String alipayPayText;

    /**
     * 医保个人账户余额展示文案（本笔扣款后剩余；由 {@code balc − acct_pay} 装配，见
     * {@code InsuranceReceiptAccountBalanceSupport}）。
     */
    @Schema(description = "账户余额展示（扣款后）")
    private String accountBalanceText;

    /** 收费员展示名。 */
    @Schema(description = "收费员")
    private String cashierName;

    /** 收费/挂号时间格式化文案。 */
    @Schema(description = "收费时间展示")
    private String chargeTimeText;
}
