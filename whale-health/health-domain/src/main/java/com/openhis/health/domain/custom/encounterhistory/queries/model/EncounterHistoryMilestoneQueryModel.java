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

package com.openhis.health.domain.custom.encounterhistory.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 里程碑列表查询模型。
 */
@Getter
@Setter
public class EncounterHistoryMilestoneQueryModel {
    /** 主键。 */
    private Long id;
    /** 名称。 */
    private String name;
    /** 就诊主键。 */
    private Long encounterId;
    /** 请求主键。 */
    private Long requestId;
    /** 请求来源表名称。 */
    private String requestTableName;
    /** 原始机构主键。 */
    private Long originalOrganizationId;
    /** 目标机构主键。 */
    private Long targetOrganizationId;
    /** 原始位置主键。 */
    private Long originalLocationId;
    /** 目标位置主键。 */
    private Long targetLocationId;
    /** out方式编码。 */
    private String outWayCode;
    /** created时间。 */
    private LocalDateTime createdAt;
}
