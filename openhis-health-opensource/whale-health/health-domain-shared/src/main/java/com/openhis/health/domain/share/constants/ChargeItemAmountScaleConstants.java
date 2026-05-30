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

package com.openhis.health.domain.share.constants;

/**
 * 收费定价/账单金额在库内统一为「元」的标度（与 {@code numeric(19,6)} 对齐）。
 */
public interface ChargeItemAmountScaleConstants {

    /** 元金额写入定价行、账单时的统一小数位数。 */
    /**
     * CHARGE_MONETARY_SCALE。
     */
    int CHARGE_MONETARY_SCALE = 6;
}
