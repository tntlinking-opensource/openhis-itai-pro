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

package com.openhis.health.domain.workflow.slot.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
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

