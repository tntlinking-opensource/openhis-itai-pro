/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.invoice.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 开具电子发票命令。
 */
@Getter
@Setter
public class IssueElectronicInvoiceCommand {
    /** 票据主键。 */
    private Long invoiceId;
    /** 电子票据编号。 */
    private String billNo;
    /** 电子票据批次码。 */
    private String billBatchCode;
    /** 票据开具时间。 */
    private LocalDateTime billCreateTime;
    /** 操作人标识。 */
    private Long operatorId;
}
