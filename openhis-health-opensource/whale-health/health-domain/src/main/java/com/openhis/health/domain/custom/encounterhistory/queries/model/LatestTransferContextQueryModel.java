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
 * 最近一次转科上下文查询模型。
 */
@Getter
@Setter
public class LatestTransferContextQueryModel {
    /** 就诊历史主键。 */
    private Long encounterHistoryId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 原始机构主键。 */
    private Long originalOrganizationId;
    /** 目标机构主键。 */
    private Long targetOrganizationId;
    /** 原始位置主键。 */
    private Long originalLocationId;
    /** 目标位置主键。 */
    private Long targetLocationId;
    /** created时间。 */
    private LocalDateTime createdAt;
}
