/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.query;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    /**
     * 退药发放库房库位主键集合（与退药患者队列 IF-PHA-02-02 / IF-IPR-02 {@code location_id IN} 同源）；
     * 非空时仅返回 {@code MedicationDispense.location_id} 落在集合内的行。
     */
    private List<Long> locationIds;

}
