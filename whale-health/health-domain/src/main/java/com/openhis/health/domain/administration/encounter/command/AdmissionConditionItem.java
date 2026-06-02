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

package com.openhis.health.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 入院诊断项：住院就诊诊断关联（可复用门诊病情事实）。
 */
@Getter
@Setter
public class AdmissionConditionItem {

    /** 已有病情事实主键。 */
    private Long conditionId;

    /** 新开诊断时必填：诊断目录主键。 */
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    private Boolean primaryFlag;

    /** 诊断说明。 */
    private String description;
}
