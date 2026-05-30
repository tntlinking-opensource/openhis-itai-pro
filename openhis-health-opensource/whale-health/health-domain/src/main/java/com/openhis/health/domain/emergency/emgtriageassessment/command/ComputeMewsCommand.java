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

import com.openhis.health.domain.emergency.emgtriageassessment.model.VitalsInput;
import lombok.Getter;
import lombok.Setter;

/**
 * MEWS 计算入参（DM-EMG-003 / SDD §5.1.1）。
 */
@Getter
@Setter
public class ComputeMewsCommand {

    private VitalsInput vitalsInput;
    private String avpuCode;
    /**
     * 机构关闭 MEWS 时须为 {@code true}，此时返回 {@code null}（BR-EMG-ASSESS-002）。
     */
    private Boolean mewsDisabledByOrganization;
}
