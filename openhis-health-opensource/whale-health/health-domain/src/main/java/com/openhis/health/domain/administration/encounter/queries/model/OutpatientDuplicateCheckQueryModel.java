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

package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊重复挂号校验结果模型。
 */
@Getter
@Setter
public class OutpatientDuplicateCheckQueryModel {

    /**
     * 是否重复。
     */
    private Boolean duplicated;

    /**
     * 命中的就诊标识集合。
     */
    private List<Long> encounterIds;

    /**
     * 患者标识。
     */
    private Long patientId;

    /**
     * 目标科室（组织机构）标识。
     */
    private Long organizationId;
}
