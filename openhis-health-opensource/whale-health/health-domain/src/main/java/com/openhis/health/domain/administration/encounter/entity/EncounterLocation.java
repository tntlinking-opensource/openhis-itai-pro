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

package com.openhis.health.domain.administration.encounter.entity;

import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊位置子实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_encounter_location")
public class EncounterLocation extends BaseAuditedEntity {

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "form_enum")
    private String formEnum;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 置为活动位置。
     *
     * @param activeAt 生效时间
     */
    public void markActive(LocalDateTime activeAt) {
        this.statusEnum = EncounterActivityStatus.ACTIVE.getCode();
        this.startTime = activeAt;
        this.endTime = null;
    }

    /**
     * 收口位置记录：{@code status_enum=COMPLETED} 且 {@code end_time=endAt}（禁止只改状态不落结束时间）。
     *
     * @param endAt 收口生效时刻，不可为空
     */
    public void markCompleted(LocalDateTime endAt) {
        if (endAt == null) {
            throw new IllegalArgumentException("位置收口时刻不能为空");
        }
        this.statusEnum = EncounterActivityStatus.COMPLETED.getCode();
        this.endTime = endAt;
    }
}

