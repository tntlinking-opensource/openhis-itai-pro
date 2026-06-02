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
