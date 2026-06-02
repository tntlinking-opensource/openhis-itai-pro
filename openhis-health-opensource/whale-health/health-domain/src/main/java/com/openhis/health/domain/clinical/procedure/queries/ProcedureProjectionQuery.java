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

package com.openhis.health.domain.clinical.procedure.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 执行记录投影查询入参。
 */
@Getter
@Setter
public class ProcedureProjectionQuery {
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 类别枚举编码。 */
    private String categoryEnum;
}

