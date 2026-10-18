/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 号源时段外部标识（DM-WKF-010）。
 *
 * <p>对应表：wkf_slot_identifier。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_slot_identifier")
public class SlotIdentifier extends BaseAuditedEntity {

    /** 号源时段标识。 */
    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    /** 标识体系。 */
    @Column(name = "id_system", nullable = false)
    private String idSystem;

    /** 标识值。 */
    @Column(name = "id_value", nullable = false)
    private String idValue;

    /** 用途编码。 */
    @Column(name = "use_code")
    private String useCode;
}
