/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.result;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 博思开具/查票/冲红成功后的统一结果 DTO（domain 层）。
 *
 * <p>由 gateway {@code IssueOutputParser} 从 {@code BossInvokeResult} 映射；
 * 冲红场景下 {@code billNo} 可能为红票号，红票专用字段 {@code eScarlet*} 由
 * {@code BossWriteOffService#markSuccess} 单独持久化到 document 冲红列。</p>
 */
@Data
public class BossInvoiceIssueResult {

    /** 博思电子票据号码。 */
    private String billNo;

    /** 博思批次号。 */
    private String batchCode;

    /** 博思校验码。 */
    private String random;

    /** 票据二维码内容。 */
    private String billQrCode;

    /** 票据图片内网地址。 */
    private String pictureUrl;

    /** 票据图片外网地址。 */
    private String pictureNetUrl;

    /** 博思返回的开具时间。 */
    private LocalDateTime billCreateTime;

    /** 业务流水号（与 adm_invoice.bus_no 对齐）。 */
    private String busNo;

    /** 开票总额。 */
    private BigDecimal totalAmt;
}
