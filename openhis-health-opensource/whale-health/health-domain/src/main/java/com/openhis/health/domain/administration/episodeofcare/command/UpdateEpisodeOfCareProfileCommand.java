/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.episodeofcare.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新诊疗段of照护档案命令。
 */
@Getter
@Setter
public class UpdateEpisodeOfCareProfileCommand {
    /** 治疗周期主键。 */
    private Long episodeOfCareId;

    /** 照护团队标识。 */
    private Long careTeamId;

    /** 照护类型。 */
    private String careType;

    /** 照护目标。 */
    private String careGoal;

    /** 操作人标识。 */
    private Long operatorId;
}
