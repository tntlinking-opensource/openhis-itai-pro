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

package com.openhis.health.domain.diagnosis.vitalsign.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 按患者与时间区间查询生命体征条件（SDD §5.2 listByPatientAndDateRange 入参）。
 */
@Getter
@Setter
public class VitalSignPatientDateRangeQuery {

    /** 患者标识（必填）。 */
    private Long patientId;

    /** 区间起始（必填）。 */
    private LocalDateTime from;

    /** 区间结束（必填）。 */
    private LocalDateTime to;
}
