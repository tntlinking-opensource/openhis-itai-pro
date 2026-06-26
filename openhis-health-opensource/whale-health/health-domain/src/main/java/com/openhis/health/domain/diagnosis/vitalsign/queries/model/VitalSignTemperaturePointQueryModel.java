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

package com.openhis.health.domain.diagnosis.vitalsign.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 体温单曲线点读模型（仅主表，DM-DIA-002 v2.0 §4.6.2）。
 */
@Getter
@Setter
public class VitalSignTemperaturePointQueryModel {

    /** 主表 id。 */
    private Long id;

    /** 记录时点。 */
    private LocalDateTime recordedAt;

    /** 体温。 */
    private String temperature;

    /** 单据类型 code。 */
    private String typeEnum;
}
