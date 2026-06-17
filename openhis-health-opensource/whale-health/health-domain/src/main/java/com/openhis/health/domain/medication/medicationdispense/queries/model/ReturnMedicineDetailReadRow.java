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
 * 门诊药房退药明细行（待退/已退发放事实 + 药嘱装配，供 AS-OP-PHA-002 明细映射）。
 *
 * <p>退药读侧<strong>不</strong>携带原正向发药单主键；应退金额由 App 按 {@code refundOriginRequestId}（{@code service_id}）汇总。</p>
 */
@Getter
@Setter
public class ReturnMedicineDetailReadRow {

    private Long dispenseId;
    private Long requestId;
    private Long refundOriginRequestId;
    private Long itemId;
    private String itemName;
    private String lotNumber;
    private BigDecimal quantity;
    private String unitCode;
    private String unitCodeText;
    private BigDecimal totalPrice;
    private String doctorName;
    private String refundEnum;
    private String refundEnumText;
    private String reqStatus;
    private String reqStatusText;
    private String serviceTable;
    private String traceNo;
}
