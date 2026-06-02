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

package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建待退药发放单命令。
 */
@Getter
@Setter
public class CreatePendingRefundDispenseCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 原始发放单标识。 */
    private Long originDispenseId;
    /** 退药请求标识。 */
    private Long refundRequestId;
    /** 退药数量。 */
    private BigDecimal refundQuantity;
}
