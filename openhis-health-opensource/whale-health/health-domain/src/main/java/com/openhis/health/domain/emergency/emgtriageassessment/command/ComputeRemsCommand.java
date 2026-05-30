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

package com.openhis.health.domain.emergency.emgtriageassessment.command;

import lombok.Getter;
import lombok.Setter;

/**
 * REMS 计算入参（SDD §5.1.3）。
 */
@Getter
@Setter
public class ComputeRemsCommand {

    private Integer patientAgeYears;
    private Integer pulse;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer respiratoryRate;
    private Integer gcs;
    private Integer spo2;
}
