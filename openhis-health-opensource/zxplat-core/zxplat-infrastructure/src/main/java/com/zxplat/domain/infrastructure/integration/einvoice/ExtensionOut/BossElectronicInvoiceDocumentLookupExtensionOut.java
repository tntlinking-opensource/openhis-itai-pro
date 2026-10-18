/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.einvoice.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 博思电子票据单据只读摘要（按 paymentId 查询）。
 *
 * <p>供 4101A 等医保上报取 {@code bill_code}/{@code bill_no}；不暴露 einvoice 实体。</p>
 */
@Data
@Accessors(chain = true)
public class BossElectronicInvoiceDocumentLookupExtensionOut {

    /** 博思单据主键。 */
    private Long documentId;

    /** 关联 health 票据主键。 */
    private Long invoiceId;

    /** 支付单主键。 */
    private Long paymentId;

    /**
     * 电子票据代码（博思 {@code boss_batch_code} / 平台 {@code billBatchCode}）。
     *
     * <p>对应 4101A {@code bill_code}。</p>
     */
    private String billBatchCode;

    /** 电子票据号码（博思 {@code boss_bill_no}）。 */
    private String bossBillNo;

    /** 业务流水号（与 {@code adm_invoice.bus_no} 对齐）。 */
    private String busNo;
}
