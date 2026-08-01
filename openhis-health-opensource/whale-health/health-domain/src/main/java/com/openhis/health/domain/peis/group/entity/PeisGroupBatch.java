/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "peis_group_batch")
public class PeisGroupBatch extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "batch_name", nullable = false, length = 128)
    private String batchName;

    @Column(name = "package_activity_id", nullable = false)
    private Long packageActivityId;

    @Column(name = "booking_date")
    private LocalDate bookingDate;

    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode = "DRAFT";
}
