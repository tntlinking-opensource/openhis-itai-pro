/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.invoice.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 发票查询命令。
 * <p>
 * 领域层查询入参，按发票主键或关联支付标识检索发票数据。
 */
@Getter
@Setter
public class InvoiceQueryCommand {
    /** 发票主键（精确查询）。 */
    private Long invoiceId;
    /** 关联支付主键（按收款反查发票）。 */
    private Long paymentId;
}
