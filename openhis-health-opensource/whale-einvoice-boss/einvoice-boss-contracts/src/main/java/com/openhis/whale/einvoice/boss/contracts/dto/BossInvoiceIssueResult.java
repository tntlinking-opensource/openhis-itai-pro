package com.openhis.whale.einvoice.boss.contracts.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 博思开具成功后的结果快照（供回写 health 与展示 QR）。
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
