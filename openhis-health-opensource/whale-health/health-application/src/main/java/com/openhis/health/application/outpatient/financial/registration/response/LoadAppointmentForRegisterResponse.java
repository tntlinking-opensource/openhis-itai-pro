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

package com.openhis.health.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * IF-REG-12：挂号台有效预约快照响应。
 */
@Data
@Accessors(chain = true)
/** 挂号台有效预约快照。 */
@Schema(description = "挂号台有效预约快照")
public class LoadAppointmentForRegisterResponse {

    @Schema(description = "预约主键")
    /** 预约主键（业务字段）。 */
    private Long appointmentId;

    /** 预约单号。 */
    @Schema(description = "预约单号")
    /** 预约单号（业务字段）。 */
    private String appointmentBusNo;

    /** 号源主键。 */
    @Schema(description = "号源主键")
    /** 号源主键（业务字段）。 */
    private Long slotId;

    /** 科室主键。 */
    @Schema(description = "科室主键")
    /** 科室主键（业务字段）。 */
    private Long organizationId;

    /** 医师主键。 */
    @Schema(description = "医师主键")
    /** 医师主键（业务字段）。 */
    private Long practitionerId;

    /** 号源类型主键。 */
    @Schema(description = "号源类型主键")
    /** 号源类型主键（业务字段）。 */
    private Long healthcareServiceId;

    /** 时段开始。 */
    @Schema(description = "时段开始")
    /** 时段开始（业务字段）。 */
    private LocalDateTime slotStart;

    /** 时段结束。 */
    @Schema(description = "时段结束")
    /** 时段结束（业务字段）。 */
    private LocalDateTime slotEnd;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    /** 科室名称（业务字段）。 */
    private String organizationName;

    /** 医师姓名。 */
    @Schema(description = "医师姓名")
    /** 医师姓名（业务字段）。 */
    private String practitionerName;

    /** 号源类型名称。 */
    @Schema(description = "号源类型名称")
    /** 号源类型名称（业务字段）。 */
    private String serviceTypeName;

    /** 预约状态。 */
    @Schema(description = "预约状态")
    /** 预约状态（业务字段）。 */
    private String statusEnum;
}
