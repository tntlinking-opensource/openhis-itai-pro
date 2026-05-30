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

@Getter
@Setter
@Entity
@Table(name = "adm_episode_of_care")
public class EpisodeOfCare extends BaseAuditedEntity {
    /** patient 标识。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;
    /** organization 标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    /** bus 编号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;
    /** careTeam 标识。 */
    @Column(name = "care_team_id")
    private Long careTeamId;
    /** careType。 */
    @Column(name = "care_type")
    private String careType;
    /** careGoal。 */
    @Column(name = "care_goal")
    private String careGoal;
    /** status 枚举值。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;
    /** active 标志位。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;
    /** start 时间。 */
    @Column(name = "start_time")
    private LocalDateTime startTime;
    /** end 时间。 */
    @Column(name = "end_time")
    private LocalDateTime endTime;
}
