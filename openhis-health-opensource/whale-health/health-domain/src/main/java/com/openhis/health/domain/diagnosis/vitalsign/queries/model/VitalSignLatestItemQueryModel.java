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
 * 就诊下各字典项最新测量读模型（SDD §4.6.2，供 getLatestByEncounterId）。
 */
@Getter
@Setter
public class VitalSignLatestItemQueryModel {

    /** 术语体系 code。 */
    private String termCode;

    /** 字典项 code。 */
    private String itemCode;

    /** 最新测量值。 */
    private String itemValue;

    /** 计量单位。 */
    private String units;

    /** 该字典项最新记录时点。 */
    private LocalDateTime recordedAt;
}
