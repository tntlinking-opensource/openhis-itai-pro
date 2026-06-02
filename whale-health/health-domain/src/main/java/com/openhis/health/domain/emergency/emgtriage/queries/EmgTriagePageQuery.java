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

package com.openhis.health.domain.emergency.emgtriage.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 分诊分页列表过滤条件（占位扩展：姓名/拼音/证件/手机等由应用层传入或与患者表联接）。
 */
@Getter
@Setter
public class EmgTriagePageQuery {

    /** 挂号科室 / 分诊机构过滤（工作台列表）。 */
    private Long organizationId;

    private Long patientId;
    private Long encounterId;
    private Integer triageLevelEnum;
    private String patientNameHint;

    /**
     * 为 true 且 {@code patientId} 非空时：分页不按 {@code ACTIVE} 过滤，用于患者维度历史（含作废、已取代）。
     */
    private Boolean includeAllTriageStatuses;
}
