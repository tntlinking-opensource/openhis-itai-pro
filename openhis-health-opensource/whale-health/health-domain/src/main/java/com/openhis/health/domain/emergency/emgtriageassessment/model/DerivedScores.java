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

import java.math.BigDecimal;

/**
 * 衍生分值（DM-EMG-003 §4.3）。
 */
@Getter
@Setter
public class DerivedScores {

    private Integer mewsScore;
    private BigDecimal traumaRts;
    private Double remsScore;
}
