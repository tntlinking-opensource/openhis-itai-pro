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

import java.math.BigDecimal;

/**
 * 创建草稿发票命令。
 * <p>
 * 基于患者与收款（支付）上下文生成草稿发票，用于后续正式开票或冲红流程。
 */
@Getter
@Setter
public class CreateDraftInvoiceCommand {
    /** 患者主键。 */
    private Long patientId;
    /** 关联的支付/收款主键。 */
    private Long paymentId;
    /** 发票类型字典编码。 */
    private String typeCode;
    /** 发票金额。 */
    private BigDecimal amount;
    /** 创建草稿的操作人标识。 */
    private Long operatorId;
}
