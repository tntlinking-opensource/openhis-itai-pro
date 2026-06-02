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

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 进入退费办理窗口命令：批量将支付单标记为可退费处理状态，由操作人触发。
 */
@Getter
@Setter
/**
 * EnterRefundProcessingWindowCommand。
 */
public class EnterRefundProcessingWindowCommand {
    /** 待进入退费流程的支付单主键列表。 */
    /**
     * paymentIds。
     */
    private List<Long> paymentIds;
    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
