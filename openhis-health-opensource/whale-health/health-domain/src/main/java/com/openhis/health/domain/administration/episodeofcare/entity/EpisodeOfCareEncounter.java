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

package com.openhis.health.domain.administration.episodeofcare.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 疗程-就诊关联实体：记录某次就诊加入/离开疗程的时间。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_episode_of_care_encounter")
public class EpisodeOfCareEncounter extends BaseAuditedEntity {
    /** 疗程主键。 */
    @Column(name = "episode_of_care_id", nullable = false)
    private Long episodeOfCareId;
    /** 就诊主键。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;
    /** 纳入疗程时间。 */
    @Column(name = "join_time", nullable = false)
    private LocalDateTime joinTime;
    /** 离开疗程时间（未离开时为空）。 */
    @Column(name = "leave_time")
    private LocalDateTime leaveTime;
}
