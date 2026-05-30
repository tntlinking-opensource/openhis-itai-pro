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
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 分诊工作台列表行（源自 {@code EmgTriageListItemQueryModel}）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊工作台列表行")
public class EmgTriageWorkbenchRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "分诊主键")
    private Long emgTriageId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识")
    private Long encounterId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识")
    private Long patientId;

    @Schema(description = "患者姓名（列表展示）")
    private String patientName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "分诊时间")
    private LocalDateTime triageTime;

    @Schema(description = "最终分级")
    private Integer triageLevelEnum;

    @Schema(description = "建议分级")
    private Integer suggestedLevelEnum;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室")
    private Long organizationId;

    @Schema(description = "分诊科室名称（列表展示）")
    private String organizationName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主去向")
    private Long locationId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "次要去向")
    private Long secondaryLocationId;

    @Schema(description = "去向模式")
    private String destinationModeEnum;

    @Schema(description = "分诊去向（主/次名称汇总，列表展示）")
    private String destinationDisplay;

    @Schema(description = "队列优先级")
    private Integer queuePriority;

    @Schema(description = "是否绿通")
    private Boolean isGreenChannel;

    @Schema(description = "是否暂存")
    private Boolean isStaged;

    @Schema(description = "分诊状态：ACTIVE / VOIDED / SUPERSEDED 等")
    private String triageStatusEnum;
}
