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

/**
 * 就诊投影分页行查询模型。
 */
@Getter
@Setter
public class EncounterProjectionQueryModel {

    /**
     * 就诊标识。
     */
    private Long encounterId;

    /**
     * 患者姓名（当前阶段由患者标识字符串映射占位）。
     */
    private String patientName;

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
