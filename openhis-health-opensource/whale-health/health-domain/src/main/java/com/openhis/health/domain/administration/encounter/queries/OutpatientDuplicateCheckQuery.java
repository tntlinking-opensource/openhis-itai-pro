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

import java.time.LocalDate;

/**
 * 门诊重复挂号校验查询入参。
 */
@Getter
@Setter
public class OutpatientDuplicateCheckQuery {

    /**
     * 患者标识。
     */
    private Long patientId;

    /**
     * 目标科室（组织机构）标识。
     */
    private Long organizationId;

    /**
     * 拟挂号业务自然日（与开立就诊的 occurredAt 日历日一致）；为空则按调用时刻的系统日期。
     */
    private LocalDate registrationDate;
}
