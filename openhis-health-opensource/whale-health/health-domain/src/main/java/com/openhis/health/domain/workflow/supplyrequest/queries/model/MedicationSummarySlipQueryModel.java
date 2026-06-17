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

package com.openhis.health.domain.workflow.supplyrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 住院护士站汇总领药 · 药品汇总单头（**AS-IP-NUR-010 · IF-IP-SUM-02**；type 27）。
 */
@Getter
@Setter
public class MedicationSummarySlipQueryModel {

    /** 汇总单号 / summaryNo。 */
    private String busNo;

    /** 申请时间。 */
    private LocalDateTime applyTime;

    /** 发药时间（已核发时有值；取自 SupplyDelivery.receive_time）。 */
    private LocalDateTime dispenseTime;

    /** 发放药房主键（purpose_location_id）。 */
    private Long purposeLocationId;

    /** 申请人主键。 */
    private Long applicantId;

    /** 领药人主键（SupplyDelivery.receiver_id；药房核发后才有值）。 */
    private Long receiverId;

    /** 汇总单审批态持久化码（SupplyRequest.status_enum）。 */
    private String supplyStatusEnum;

    /** 发放状态持久化码（SupplyDelivery.status_enum；药房核发后才有值）。 */
    private String deliveryStatusEnum;
}
