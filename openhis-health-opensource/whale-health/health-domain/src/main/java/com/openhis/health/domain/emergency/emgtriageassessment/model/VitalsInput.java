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

package com.openhis.health.domain.emergency.emgtriageassessment.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 体征文本入参（DM-EMG-003 §4.1）。
 */
@Getter
@Setter
public class VitalsInput {

    private String temperature;
    private String pulse;
    private String respiration;
    private String bpSystolic;
    private String bpDiastolic;
    private String spo2;
    /** 意识 AVPU 文本或编码；亦可仅通过 {@code ComputeMewsCommand.avpuCode} 传入。 */
    private String mewsAvpu;
}
