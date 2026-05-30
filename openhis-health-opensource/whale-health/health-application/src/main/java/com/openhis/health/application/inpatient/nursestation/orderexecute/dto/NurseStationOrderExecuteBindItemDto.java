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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 医嘱执行绑定项（IF-IP-EXE-01/02，BR-IP-EXE-013/020）。
 *
 * <p>{@code activityId} 与 {@code deviceId} 互斥有值：绑定诊疗走 activity 分支，
 * 绑定耗材走 device 分支；写路径由 AppService 按非空字段分流。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行绑定项")
public class NurseStationOrderExecuteBindItemDto {

    /** 绑定类型：USAGE_TO_DEVICE / ACTIVITY_CHILD。 */
    @Schema(description = "绑定类型")
    private String bindType;

    /** 绑定诊疗活动定义主键（诊疗绑定时有值）。 */
    @Schema(description = "绑定诊疗活动定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 绑定耗材定义主键（耗材绑定时有值）。 */
    @Schema(description = "绑定耗材定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceId;

    /** 绑定项展示名。 */
    @Schema(description = "绑定项展示名")
    private String displayName;

    /** 数量分子。 */
    @Schema(description = "数量分子")
    private BigDecimal quantityNumerator;

    /** 数量分母。 */
    @Schema(description = "数量分母")
    private BigDecimal quantityDenominator;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitCode_dictText;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer priority;
}
