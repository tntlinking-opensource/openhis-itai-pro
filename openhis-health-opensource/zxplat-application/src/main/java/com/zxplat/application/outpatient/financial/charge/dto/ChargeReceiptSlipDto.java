/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊收费小票 PrintData（与 op-charge-print 模板 field 一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费小票")
public class ChargeReceiptSlipDto {

    /** 就诊主键 {@code adm_encounter.id}，与 op-charge-print 模板 field 一致。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 患者姓名（读模型投影，小票抬头）。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 机构全称（小票抬头 hospitalName）。 */
    @Schema(description = "机构名称")
    private String hospitalName;

    /** 性别中文展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄展示文案。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 费用性质/合同展示（自费/医保等）。 */
    @Schema(description = "费用性质")
    private String feeNatureText;

    /** 收费项目明细行（模板 chargeItemLines）。 */
    @Schema(description = "收费项目行")
    private List<ChargeReceiptChargeLineDto> chargeItemLines;

    /** 合计金额格式化文案（含货币符号）。 */
    @Schema(description = "合计金额展示")
    private String totalAmountText;

    /** 统筹支付格式化文案（基本医保统筹 hifp_pay）。 */
    @Schema(description = "统筹支付展示")
    private String poolingPayText;

    /** 基金支付合计格式化文案（fund_pay_sumamt）。 */
    @Schema(description = "基金支付合计展示")
    private String fundPayText;

    /** 其他支出格式化文案（oth_pay，含电子钱包等）。 */
    @Schema(description = "其他支出展示")
    private String othPayText;

    /** 医保个人账户支付展示文案。 */
    @Schema(description = "医保个人账户支付展示")
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

    /** 现金实收展示文案（CASH 渠道；无扫码渠道时可能回落医保个人现金）。 */
    @Schema(description = "现金实收展示")
    private String cashPayText;

    /** 微信实收展示文案。 */
    @Schema(description = "微信实收展示")
    private String wechatPayText;

    /** 支付宝实收展示文案。 */
    @Schema(description = "支付宝实收展示")
    private String alipayPayText;

    /**
     * 医保个账余额展示文案（本笔扣款后剩余；优先 2207 {@code balc} 原样，见
     * {@code InsuranceReceiptAccountBalanceSupport}）。
     */
    @Schema(description = "医保个账余额展示（扣款后）")
    private String accountBalanceText;

    /** 收费员展示名。 */
    @Schema(description = "收费员")
    private String cashierName;

    /** 收费/结算时间格式化文案。 */
    @Schema(description = "收费时间展示")
    private String chargeTimeText;

    /** 门诊号（就诊业务号）。 */
    @Schema(description = "门诊号")
    private String encounterBusNo;

    /**
     * 挂号时间展示（就诊 {@code startTime}，{@code yyyy-MM-dd HH:mm:ss}）。
     *
     * <p>与 op-charge-print 模板 {@code registerTimeText} 一致。</p>
     */
    @Schema(description = "挂号时间")
    private String registerTimeText;

    /** 科室名称（就诊科室；发票/费用清单沿用）。 */
    @Schema(description = "科室")
    private String departmentName;

    /** 接诊医生姓名（发票/费用清单回落）。 */
    @Schema(description = "医生")
    private String doctorName;

    /**
     * 开方科室（本笔父收费项开立机构，多项以顿号连接；无开立机构时回落就诊科室）。
     *
     * <p>与 op-charge-print 模板 {@code orderingDepartmentName} 一致。</p>
     */
    @Schema(description = "开方科室")
    private String orderingDepartmentName;

    /**
     * 开方医生（医生开立 Request 的开立人，多项以顿号连接；无开立人时回落接诊医生）。
     *
     * <p>与 op-charge-print 模板 {@code orderingDoctorName} 一致。</p>
     */
    @Schema(description = "开方医生")
    private String orderingDoctorName;

    /** 处方号（本笔收费涉及的处方号，多项以顿号连接）。 */
    @Schema(description = "处方号")
    private String prescriptionNoText;

    /** 支付单业务号。 */
    @Schema(description = "收费单号")
    private String paymentNoText;

    /** 医保人员编号。 */
    @Schema(description = "医保号")
    private String insuranceNoText;

    /** 收费日期（yyyy/MM/dd，针式发票日期栏）。 */
    @Schema(description = "收费日期")
    private String chargeDateText;

    /** 西药费；无金额时为空串。 */
    @Schema(description = "西药费")
    private String westernMedicineFeeText;

    /** 检查费；无金额时为空串。 */
    @Schema(description = "检查费")
    private String examFeeText;

    /** 中成药费；无金额时为空串。 */
    @Schema(description = "中成药费")
    private String chinesePatentFeeText;

    /** 手术费；无金额时为空串。 */
    @Schema(description = "手术费")
    private String surgeryFeeText;

    /** 中草药费；无金额时为空串。 */
    @Schema(description = "中草药费")
    private String herbalFeeText;

    /** 输氧费；无金额时为空串。 */
    @Schema(description = "输氧费")
    private String oxygenFeeText;

    /** 化验费；无金额时为空串。 */
    @Schema(description = "化验费")
    private String labFeeText;

    /** 输血费；无金额时为空串。 */
    @Schema(description = "输血费")
    private String bloodFeeText;

    /** X光费；无金额时为空串。 */
    @Schema(description = "X光费")
    private String xrayFeeText;

    /** 处置费；无金额时为空串。 */
    @Schema(description = "处置费")
    private String disposeFeeText;

    /** 电诊费；无金额时为空串。 */
    @Schema(description = "电诊费")
    private String electroFeeText;

    /** 注射费；无金额时为空串。 */
    @Schema(description = "注射费")
    private String injectionFeeText;

    /** 治疗费；无金额时为空串。 */
    @Schema(description = "治疗费")
    private String therapyFeeText;

    /** 耗材费；无金额时为空串。未归类金额也落入本栏；CT/DR/核磁归检查费，不进本栏。 */
    @Schema(description = "耗材费")
    private String materialFeeText;

    /** 原个人账户金额（扣款前）。 */
    @Schema(description = "原个人账户金额")
    private String originalAccountBalanceText;

    /** 医保支付（基金支付总额）。 */
    @Schema(description = "医保支付")
    private String insurancePayText;

    /** 发票现金支付（现金/微信/支付宝/银行卡合计，对应个人现金）。 */
    @Schema(description = "现金支付")
    private String invoiceCashPayText;

    /** 职工大病（职工大额医疗费用补助）。 */
    @Schema(description = "职工大病")
    private String employeeCriticalFeeText;

    /** 居民大病（居民大病保险资金支出）。 */
    @Schema(description = "居民大病")
    private String residentCriticalFeeText;

    /** 总费用人民币大写。 */
    @Schema(description = "总费用大写")
    private String totalAmountChineseText;

    /**
     * 费用清单标题（普通「门诊患者费用清单」/ 慢病「门诊慢病患者费用清单」）。
     *
     * <p>与 op-feeList-print 模板 {@code documentTitle} 一致。</p>
     */
    @Schema(description = "费用清单标题")
    private String documentTitle;

    /**
     * 机构行展示（如「机构：南关国基医院」）。
     *
     * <p>与 op-feeList-print 模板 {@code organizationLabel} 一致。</p>
     */
    @Schema(description = "机构行展示")
    private String organizationLabel;

    /**
     * 门诊患者费用清单明细行。
     *
     * <p>与 op-feeList-print 模板 {@code feeListRows} 一致。</p>
     */
    @Schema(description = "费用清单明细行")
    private List<ChargeFeeListRowDto> feeListRows;

    /**
     * 费用清单合计金额展示（明细行总价之和，无货币符号）。
     *
     * <p>与 op-feeList-print 模板 {@code feeListTotalAmountText} 一致。</p>
     */
    @Schema(description = "费用清单合计金额")
    private String feeListTotalAmountText;
}
