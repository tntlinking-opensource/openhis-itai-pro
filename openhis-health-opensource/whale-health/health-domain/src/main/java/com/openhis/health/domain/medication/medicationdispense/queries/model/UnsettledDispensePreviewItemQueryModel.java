/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房交账未挂载发药预览行（DM-FIN-003 · med_medication_dispense 候选）。
 */
@Getter
@Setter
public class UnsettledDispensePreviewItemQueryModel {

    /** 发药行主键。 */
    private Long dispenseId;

    /** 业务单号 {@code bus_no}。 */
    private String busNo;

    /** 发放状态 code（{@link com.openhis.health.domain.share.enums.DispenseStatus}）。 */
    private String statusEnum;

    /** 发放类型 code（{@link com.openhis.health.domain.share.enums.MedicationDispenseType}）。 */
    private String dispenseEnum;

    /** 药品主键。 */
    private Long medicationId;

    /** 发药库房。 */
    private Long locationId;

    /** 核发药师 {@code practitioner_id}。 */
    private Long practitionerId;

    /** 发药时间；候选组装遇空由读侧抛错。 */
    private LocalDateTime dispenseTime;

    /** 实发数量 {@code dispense_quantity}。 */
    private BigDecimal dispenseQuantity;
}
