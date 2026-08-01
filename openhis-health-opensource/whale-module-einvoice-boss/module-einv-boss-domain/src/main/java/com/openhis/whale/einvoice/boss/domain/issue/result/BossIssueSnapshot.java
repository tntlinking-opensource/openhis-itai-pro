/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.result;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单次博思开票所需的 health 侧读模型快照（domain 层）。
 *
 * <p>由 {@link com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader}
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

    /** 博思医保类型名称（如全自费）。 */
    private String medicalCareType;

    /** 博思医保类型编码（如 07）。 */
    private String medCareTypeCode;

    /** 就诊科室名称。 */
    private String patientCategory;

    /** 就诊科室编码。 */
    private String patientCategoryCode;

    /** 收费明细行。 */
    private List<BossChargeLineSnapshot> chargeLines = new ArrayList<>();
}
