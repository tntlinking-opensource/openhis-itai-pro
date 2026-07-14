/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.invoice.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 作废发票命令。
 * <p>
 * 对已开具或待开具的发票执行作废，需记录作废原因与操作人，以满足财务合规。
 */
@Getter
@Setter
public class CancelInvoiceCommand {
    /** 待作废的发票主键。 */
    private Long invoiceId;
    /** 作废原因说明。 */
    private String reason;
    /** 执行作废操作的操作人标识。 */
    private Long operatorId;
}
