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

import com.openhis.health.domain.emergency.emgtriageassessment.model.DerivedScores;
import com.openhis.health.domain.emergency.emgtriageassessment.model.ScaleInput;
import com.openhis.health.domain.emergency.emgtriageassessment.model.VitalsInput;
import lombok.Getter;
import lombok.Setter;

/**
 * 建议分级规则入参（SDD §5.1.4）。
 */
@Getter
@Setter
public class SuggestTriageLevelCommand {

    private DerivedScores derivedScores;
    private VitalsInput vitalsInput;
    private ScaleInput scaleInput;
    private String patientProfileKey;
}
