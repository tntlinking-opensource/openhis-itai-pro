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
 * 将就诊纳入疗程命令：建立疗程与就诊的关联，用于跨次就诊归集。
 */
@Getter
@Setter
public class AddEncounterToEpisodeCommand {
    /** 疗程主键。 */
    private Long episodeOfCareId;
    /** 待纳入的就诊主键。 */
    private Long encounterId;
    /** 操作人主键。 */
    private Long operatorId;
}
