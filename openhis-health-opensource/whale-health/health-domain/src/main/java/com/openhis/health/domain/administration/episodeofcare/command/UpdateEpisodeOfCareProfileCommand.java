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
