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
 * 撤销调价草稿命令（DM-CUS-011 §5.1.6）。
 *
 * <p>仅 draft → deprecated；不涉及库存解锁。</p>
 */
@Getter
@Setter
public class WithdrawPriceAdjustmentDraftCommand {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 操作人（审计）。 */
    private Long operatorId;
}
