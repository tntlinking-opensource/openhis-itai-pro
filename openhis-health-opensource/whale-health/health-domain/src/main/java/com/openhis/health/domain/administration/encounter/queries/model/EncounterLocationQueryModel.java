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

package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊位置轨迹读模型：单条位置占用记录及起止时间。
 */
@Getter
@Setter
public class EncounterLocationQueryModel {
    /** 位置记录主键。 */
    private Long locationRecordId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 地点主键。 */
    private Long locationId;
    /** 地点形态枚举编码。 */
    private String formEnum;
    /** 占用状态枚举编码。 */
    private String statusEnum;
    /** 占用开始时间。 */
    private LocalDateTime startTime;
    /** 占用结束时间。 */
    private LocalDateTime endTime;
}
