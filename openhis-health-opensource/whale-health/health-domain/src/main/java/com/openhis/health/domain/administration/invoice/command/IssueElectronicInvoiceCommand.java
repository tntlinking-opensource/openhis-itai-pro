/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
