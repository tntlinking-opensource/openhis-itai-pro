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

package com.openhis.health.domain.medication.medicationdispense.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊药房退药明细查询（AS-OP-PHA-002-SDD IF-PHA-02-03）。
 */
@Getter
@Setter
public class ReturnMedicineDetailQuery {

    /**
     * 就诊主键。
     */
    private Long encounterId;

    /**
     * 退药状态过滤（{@code PENDING_REFUND}/{@code REFUNDED} 之 {@code DispenseStatus#getCode()}）；空表示不过滤。
     */
    private String refundStatus;

}
