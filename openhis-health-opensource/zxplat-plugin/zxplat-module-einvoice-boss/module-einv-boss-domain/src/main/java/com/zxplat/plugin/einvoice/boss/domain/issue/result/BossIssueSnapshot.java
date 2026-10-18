/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.result;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单次博思开票所需的 health 侧读模型快照（domain 层）。
 *
 * <p>由 {@link com.zxplat.plugin.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader}
 * 加载，贯穿 payload 组装、金额校验、回写操作人识别全流程。字段与博思
 * {@code issueEBill} 报文字段一一对应或经 gateway assembler 转换。</p>
 */
@Data
public class BossIssueSnapshot {

    /** 票据主键。 */
    private Long invoiceId;

    /** 支付主键。 */
    private Long paymentId;

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 租户主键。 */
    private Long tenantId;

    /** 票据类型码（REG/OUTPATIENT）。 */
    private String invoiceTypeCode;

    /** health 票据状态（DRAFT/ISSUED/CANCELLED）。 */
    private String invoiceStatus;

    /** 业务流水号（adm_invoice.bus_no）。 */
    private String busNo;

    /** 开票总额。 */
    private BigDecimal totalAmt;

    /** 支付展示金额。 */
    private BigDecimal paymentAmt;

    /** 患者姓名。 */
    private String patientName;

    /** 性别展示（男/女）。 */
    private String genderDisplay;

    /** 年龄字符串。 */
    private String age;

    /** 身份证号。 */
    private String idCard;

    /** 手机号。 */
    private String phone;

    /** 就诊业务号。 */
    private String encounterBusNo;

    /** 病历号（取 encounter.bus_no；博思 v4 院点必填）。 */
    private String caseNumber;

    /** 患者住院号（I 线必填，默认同 encounterBusNo）。 */
    private String hospitalNo;

    /** 住院就诊编号（I 线必填）。 */
    private String visitNo;

    /** 业务发生时间。 */
    private LocalDateTime billDateTime;

    /** health 票号（adm_invoice.bill_no，已开具时有值）。 */
    private String healthBillNo;

    /** health 开具时间。 */
    private LocalDateTime issuedAt;

    /** 收费员/操作人主键。 */
    private Long operatorId;

    /** 收费员/操作人姓名（报文 payee/author/checker）。 */
    private String operatorName;

    /** 博思医保类型名称（省医保/市医保/自费等）。 */
    private String medicalCareType;

    /** 博思医保类型编码（费用性质 1～4 / 工伤 5）。 */
    private String medCareTypeCode;

    /** 患者医保编号（结算回执 psn_no）。 */
    private String medicalInsuranceId;

    /** 是否存在有效医保结算回执（决定基金分项 vs 自费口径）。 */
    private boolean insuranceSettled;

    /** 个人账户支付 acct_pay。 */
    private BigDecimal accountPay;

    /** 基本统筹基金支付 hifp_pay。 */
    private BigDecimal fundPay;

    /** 其它医保支付（公务员+企补+大病+大额+救助+医院负担+其他）。 */
    private BigDecimal otherfundPay;

    /** 全自费金额 fulamt_ownpay_amt。 */
    private BigDecimal ownPay;

    /** 个人自负（起付）act_pay_dedc。 */
    private BigDecimal selfConceitedAmt;

    /** 个人自付（先行自付）preselfpay_amt。 */
    private BigDecimal selfPayAmt;

    /** 个人现金支付 psn_cash_pay。 */
    private BigDecimal selfCashPay;

    /** 医保报销总额 fund_pay_sumamt。 */
    private BigDecimal reimbursementAmt;

    /** 符合政策范围金额。 */
    private BigDecimal inscpScpAmt;

    /** 超限价自费。 */
    private BigDecimal overlmtSelfpay;

    /** 公务员补助。 */
    private BigDecimal cvlservPay;

    /** 企业补充。 */
    private BigDecimal hifesPay;

    /** 居民大病。 */
    private BigDecimal hifmiPay;

    /** 职工大额。 */
    private BigDecimal hifobPay;

    /** 医疗救助。 */
    private BigDecimal mafPay;

    /** 医院负担。 */
    private BigDecimal hospPartAmt;

    /** 其他支出。 */
    private BigDecimal othPay;

    /** 统筹支付比例。 */
    private BigDecimal poolPropSelfpay;

    /** 就诊科室名称。 */
    private String patientCategory;

    /** 就诊科室编码。 */
    private String patientCategoryCode;

    /** 入院/住院日期（I 线博思 inHospitalDate）。 */
    private LocalDateTime inHospitalDateTime;

    /** 出院日期（I 线博思 outHospitalDate；可空时回落账单日）。 */
    private LocalDateTime outHospitalDateTime;

    /** 收费明细行。 */
    private List<BossChargeLineSnapshot> chargeLines = new ArrayList<>();
}
