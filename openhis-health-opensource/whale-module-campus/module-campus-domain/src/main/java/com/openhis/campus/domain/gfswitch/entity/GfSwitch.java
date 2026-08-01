/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfswitch.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 公费配置开关（cmp_gf_switch）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_gf_switch")
public class GfSwitch extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "switch_key", nullable = false, length = 64)
    private String switchKey;

    @Column(name = "switch_value", nullable = false, length = 512)
    private String switchValue;
}
