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

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * IF-EMG-07：修订分诊。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-修订请求")
public class AmendEmgTriageRequest {

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "分诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long emgTriageId;

    @Schema(description = "最终分级")
    private Integer triageLevelEnum;

    @Schema(description = "建议分级")
    private Integer suggestedLevelEnum;

    @Schema(description = "覆盖建议原因")
    private String levelAdjustReason;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室")
    private Long organizationId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主去向")
    private Long locationId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "次要去向")
    private Long secondaryLocationId;

    @Schema(description = "去向模式")
    private String destinationModeEnum;

    @Schema(description = "队列优先级")
    private Integer queuePriority;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "绿通")
    private Boolean isGreenChannel;

    @Schema(description = "免费挂号")
    private Boolean isFreeRegister;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "绿通发病时间")
    private LocalDateTime greenChannelOnsetTime;

    @NotNull
    @Schema(description = "变更原因分类", requiredMode = Schema.RequiredMode.REQUIRED)
    private String changeReasonCode;

    @Schema(description = "变更备注")
    private String changeRemark;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "变更发生时间")
    private LocalDateTime occurredAt;
}
