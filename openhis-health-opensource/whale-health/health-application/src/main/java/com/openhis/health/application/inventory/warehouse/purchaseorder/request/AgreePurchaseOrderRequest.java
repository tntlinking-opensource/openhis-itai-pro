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

package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审批同意请求。
 */
@Getter
@Setter
public class AgreePurchaseOrderRequest {

    /** 单据号。 */
    private String busNo;
    /** 审批人；缺省当前用户。 */
    private Long approverId;
    /** 审批时间。 */
    private LocalDateTime approvalClock;
}
