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

package com.openhis.health.domain.administration.encounter.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊投影分页查询入参。
 */
@Getter
@Setter
public class EncounterProjectionQuery {

    /**
     * 页码（从 1 开始）。
     */
    private Integer pageNo;

    /**
     * 每页条数。
     */
    private Integer pageSize;

    /**
     * 患者标识。
     */
    private Long patientId;

    /**
     * 就诊类别编码。
     */
    private String classEnum;

    /**
     * 就诊状态编码。
     */
    private String statusEnum;

    /**
     * 科室标识。
     */
    private Long organizationId;
}

