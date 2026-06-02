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

package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 护士站执行前：单库位+药品的待发预占汇总行（DRAFT/PREPARATION）。
 */
@Getter
@Setter
public class DispenseExeReserveRowModel {
    /** 药房库位主键。 */
    private Long locationId;
    /** 药品定义主键。 */
    private Long medicationId;
    /** 待发预占总量。 */
    private BigDecimal reserveQuantity;
}
