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
 * 就诊当前位置信息查询模型。
 */
@Getter
@Setter
public class EncounterLocationInfoQueryModel {

    /**
     * 就诊标识。
     */
    private Long encounterId;

    /**
     * 当前活动位置标识。
     */
    private Long currentLocationId;

    /**
     * 当前活动位置形态编码。
     */
    private String currentFormEnum;

    /**
     * 当前活动位置开始时间。
     */
    private LocalDateTime activeSince;
}

