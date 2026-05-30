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

package com.openhis.health.application.emergency.emgtriage.station.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估子项 DTO，映射 {@code SaveEmgTriageAssessmentItemCommand}。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊评估子项")
public class SaveEmgTriageAssessmentItemDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "评估行主键，更新时填写")
    private Long id;

    @Schema(description = "评估类型编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String assessmentType;

    @Schema(description = "评估原始 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
    private String assessmentDataJson;

    @Schema(description = "总分")
    private BigDecimal assessmentScore;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "评估时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime assessedAt;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "评估人")
    private Long assessedBy;
}
