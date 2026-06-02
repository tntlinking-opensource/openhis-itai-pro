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

/**
 * 撤销草稿命令。
 */
@Getter
@Setter
public class WithdrawDraftCommand {
    /**
     * 价格调整单标识。
     */
    private Long adjustmentId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}

