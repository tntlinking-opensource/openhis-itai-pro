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

package com.openhis.health.domain.emergency.emgtriageassessment.queries;

import com.openhis.health.domain.emergency.emgtriageassessment.model.ScaleInput;
import com.openhis.health.domain.emergency.emgtriageassessment.model.VitalsInput;
import lombok.Getter;
import lombok.Setter;

/**
 * 预览建议分级查询（SDD §5.2）。
 */
@Getter
@Setter
public class PreviewSuggestedLevelQuery {

    private VitalsInput vitalsInput;
    private ScaleInput scaleInput;
    private Integer patientAgeYears;
    private String patientProfileKey;
    private Boolean mewsDisabledByOrganization;
}
