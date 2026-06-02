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

package com.openhis.health.domain.workflow.schedule.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 日程外部标识（DM-WKF-009 §3.3）。
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_schedule_identifier")
public class ScheduleIdentifier extends BaseAuditedEntity {

    @Column(name = "schedule_id", nullable = false)
    /** 排班主键。 */
    private Long scheduleId;

    @Column(name = "id_system", nullable = false)
    /** 标识system。 */
    private String idSystem;

    @Column(name = "id_value", nullable = false)
    /** 标识value。 */
    private String idValue;

    @Column(name = "use_code")
    /** use编码。 */
    private String useCode;
}

