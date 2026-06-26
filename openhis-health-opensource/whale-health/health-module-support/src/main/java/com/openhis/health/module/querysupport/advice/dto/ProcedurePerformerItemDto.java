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

package com.openhis.health.module.querysupport.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行记录参与人（对齐 {@code cln_procedure_performer}，禁止对外透出 performer_list_json 串）。
 */
@Getter
@Setter
@Schema(description = "执行记录参与人")
public class ProcedurePerformerItemDto {

    /** 执行参与执业者主键（对齐 {@code cln_procedure_performer.practitioner_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执业者主键")
    private Long practitionerId;

    /** 参与职能编码（如主刀、助手、核对护士等，存术语 code）。 */
    @Schema(description = "职能编码")
    private String functionCode;

    /** 参与开始时刻（可空表示瞬时执行）。 */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /** 参与结束时刻（可空表示仍在场或未记录结束）。 */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /** 参与人来源机构标识（院外协作等场景）。 */
    @Schema(description = "来源机构标识")
    private String orgFromId;

    /** 执业者展示姓名（读侧装配，不落库）。 */
    @Schema(description = "执业者展示姓名")
    private String practitionerName;
}
