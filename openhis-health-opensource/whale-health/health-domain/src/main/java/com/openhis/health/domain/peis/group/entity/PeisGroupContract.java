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

import java.math.BigDecimal;
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

    /** 合同金额（元）。 */
    @Column(name = "contract_amount", precision = 18, scale = 2)
    private BigDecimal contractAmount;

    /** 合同支付状态：{@link com.openhis.health.domain.share.constants.PeisGroupContractConstants}。 */
    @Column(name = "pay_status_code", nullable = false, length = 32)
    private String payStatusCode;
}
