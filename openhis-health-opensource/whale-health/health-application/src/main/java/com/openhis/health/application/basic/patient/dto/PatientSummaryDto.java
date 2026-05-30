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

package com.openhis.health.application.basic.patient.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 患者简要信息展示 DTO（列表或检索摘要）。
 *
 */
@Data
public class PatientSummaryDto {
    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;
    /** 姓名。 */
    private String patientName;
    /** 性别（历史字段：可能为字典整数码，语义以前端约定为准）。 */
    private Integer gender;
    /** 证件号摘要展示。 */
    private String idCard;
}
