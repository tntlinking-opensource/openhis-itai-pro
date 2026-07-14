/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
