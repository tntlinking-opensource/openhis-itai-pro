/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊原因子实体：记录就诊事由的来源表与业务主键及用途编码。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_encounter_reason")
public class EncounterReason extends BaseAuditedEntity {

    /** 就诊主键。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 用途编码（如挂号原因类型）。 */
    @Column(name = "use_code", nullable = false)
    private String useCode;

    /** 原因实体所在逻辑表名。 */
    @Column(name = "reason_table")
    private String reasonTable;

    /** 原因业务主键。 */
    @Column(name = "reason_id")
    private Long reasonId;

    /** 原因描述。 */
    @Column(name = "description")
    private String description;
}
