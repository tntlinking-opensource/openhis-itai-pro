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

package com.openhis.health.application.inpatient.nursestation.orderexecute.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医嘱执行槽位（IF-IP-EXE-01，BR-IP-EXE-007～009）。
 *
 * <p>pending 槽位 status=PREPARATION 且无 procedureId；已执行/不执行/已取消槽位
 * 携带 procedureId 与 recordedTime。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行槽位")
public class NurseStationOrderExecuteSlotDto {

    /** 计划执行时刻（occurrence_time；deadline/Tab 过滤键）。 */
    @Schema(description = "计划执行时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planTime;

    /** 执行记录主键；pending 槽位为空。 */
    @Schema(description = "执行记录主键；pending 槽位为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long procedureId;

    /** 槽位终态 code（EventStatus）。 */
    @Schema(description = "槽位终态 code")
    private String statusEnum;

    /** 槽位终态展示。 */
    @Schema(description = "槽位终态展示")
    private String statusEnumText;

    /** 护士登记/实际执行时刻（recorded_time）。 */
    @Schema(description = "护士登记/实际执行时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordedTime;
}
