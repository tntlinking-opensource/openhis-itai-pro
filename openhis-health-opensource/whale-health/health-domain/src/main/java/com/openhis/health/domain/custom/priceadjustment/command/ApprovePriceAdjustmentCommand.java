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

package com.openhis.health.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 审批通过命令。
 */
@Getter
@Setter
public class ApprovePriceAdjustmentCommand {
    /**
     * 价格调整单标识。
     */
    private Long adjustmentId;
    /**
     * 审批人标识。
     */
    private Long approverId;
    /**
     * 审批意见（可空）。
     */
    private String approveComment;
    /**
     * 审批时间（可空；由聚合默认当前时间）。
     */
    private Date approveTime;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}

