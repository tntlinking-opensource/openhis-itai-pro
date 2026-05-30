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

package com.openhis.health.domain.administration.invoice.query;

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
