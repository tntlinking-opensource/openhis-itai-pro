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

package com.openhis.health.domain.administration.episodeofcare.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 疗程查询命令：按主键、业务单号、患者或机构筛选疗程列表/单条。
 */
@Getter
@Setter
public class EpisodeOfCareQueryCommand {
    /** 疗程主键。 */
    private Long id;
    /** 疗程业务单号。 */
    private String busNo;
    /** 患者主键。 */
    private Long patientId;
    /** 机构主键。 */
    private Long organizationId;
}
