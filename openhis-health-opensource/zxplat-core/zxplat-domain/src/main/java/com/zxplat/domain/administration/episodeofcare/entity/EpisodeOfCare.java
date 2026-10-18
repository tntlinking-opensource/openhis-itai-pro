/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.episodeofcare.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 照护周期聚合根（{@code adm_episode_of_care}）。
 *
 * <p>边界：一次患者在某机构下的连续照护 episode；与多条 {@code adm_encounter} 通过关联表挂载。</p>
 */
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
