/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.document.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 博思电子票据单据聚合根（双存储：博思字段仅存本表）。
 *
 * <p>对应表 {@code einv_boss_document}；与 {@code adm_invoice} 以 {@code invoice_id} 关联
 *（通用票 COM 可无 invoiceId）。正常一结算一票一条单据（医院不允许拆票）。
 * 生命周期：初始化 → 可选调额 → 异步开具落蓝票 → 作废冲红落红票。</p>
 *
 * <p>幂等键：{@code bus_no}；冲红并发由 {@code claimWriteOffProcessing} 原子抢占。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "einv_boss_document")
public class EinvBossDocument extends BaseAuditedEntity {

    /** 关联 health 票据主键。 */
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    /** 关联支付主键。 */
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /** 开票种类（REG/AMB/IMP）。 */
    @Column(name = "issue_kind", nullable = false, length = 10)
    private String issueKind;

    /** 业务流水号（与 adm_invoice.bus_no 对齐）。 */
    @Column(name = "bus_no", nullable = false, length = 50)
    private String busNo;

    /** 博思 busType。 */
    @Column(name = "bus_type", nullable = false, length = 10)
    private String busType;

    /** 开票总额。 */
    @Column(name = "total_amt", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmt;

    /** 支付金额。 */
    @Column(name = "payment_amt", nullable = false, precision = 14, scale = 2)
    private BigDecimal paymentAmt;

    /** 金额一致性状态。 */
    @Column(name = "amount_status", nullable = false, length = 20)
    private String amountStatus;

    /** 博思电子票据号码。 */
    @Column(name = "boss_bill_no", length = 50)
    private String bossBillNo;

    /** 博思批次号。 */
    @Column(name = "boss_batch_code", length = 50)
    private String bossBatchCode;

    /** 博思校验码。 */
    @Column(name = "random", length = 50)
    private String random;

    /** 票据图片内网地址。 */
    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    /** 票据图片外网地址。 */
    @Column(name = "picture_net_url", length = 500)
    private String pictureNetUrl;

    /** 票据二维码内容。 */
    @Column(name = "bill_qr_code", columnDefinition = "TEXT")
    private String billQrCode;

    /** 出站报文快照（排障用，禁止透出 API）。 */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** 博思平台配置档标识。 */
    @Column(name = "platform_profile", nullable = false, length = 50)
    private String platformProfile;

    /** 通用票交款人（P3 · issue_kind=COM 时使用）。 */
    @Column(name = "payer_name", length = 100)
    private String payerName;

    /** 通用票交款人手机。 */
    @Column(name = "payer_phone", length = 20)
    private String payerPhone;

    /** 通用票证件号。 */
    @Column(name = "payer_id_card", length = 30)
    private String payerIdCard;

    /** 交款人类型：1 个人 2 单位。 */
    @Column(name = "payer_type", length = 1)
    private String payerType;

    /** 交款人性别。 */
    @Column(name = "payer_sex", length = 4)
    private String payerSex;

    /** 交款人年龄。 */
    @Column(name = "payer_age", length = 10)
    private String payerAge;

    /** 通用票备注。 */
    @Column(name = "remark", length = 200)
    private String remark;

    /** 开票经办人姓名。 */
    @Column(name = "operator_name", length = 50)
    private String operatorName;

    /** 博思冲红状态（SUCCESS/FAILED/SKIPPED）。 */
    @Column(name = "write_off_status", length = 20)
    private String writeOffStatus;

    /** 博思红票号码。 */
    @Column(name = "write_off_bill_no", length = 50)
    private String writeOffBillNo;

    /** 博思红票批次号。 */
    @Column(name = "write_off_batch_code", length = 50)
    private String writeOffBatchCode;

    /** 冲红完成时间。 */
    @Column(name = "write_off_at")
    private LocalDateTime writeOffAt;

    /** 冲红失败原因或博思摘要。 */
    @Column(name = "write_off_message", length = 500)
    private String writeOffMessage;
}
