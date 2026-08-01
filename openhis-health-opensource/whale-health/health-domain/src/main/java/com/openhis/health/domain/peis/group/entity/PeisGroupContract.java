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
@Table(name = "peis_group_contract")
public class PeisGroupContract extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "package_activity_id", nullable = false)
    private Long packageActivityId;

    @Column(name = "contract_name", length = 128)
    private String contractName;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;
}
