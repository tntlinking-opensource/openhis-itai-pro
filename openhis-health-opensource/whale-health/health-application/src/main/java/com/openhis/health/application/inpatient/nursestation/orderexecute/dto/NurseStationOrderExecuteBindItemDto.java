/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /** 绑定来源类型：{@code USAGE}（用法） / {@code ACTIVITY}（诊疗）；与模板 {@code type_code} 同码。 */
    @Schema(description = "绑定类型")
    private String bindType;

    /** 诊疗耗材绑定模板主键。 */
    @Schema(description = "绑定模板主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bindingId;

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

    /** 目标业务编码。 */
    @Schema(description = "目标业务编码")
    private String busNo;

    /** 目标规格展示。 */
    @Schema(description = "目标规格展示")
    private String specificationText;

    /** 零售单价。 */
    @Schema(description = "零售单价")
    private BigDecimal unitPrice;

    /** 收费项定义主键。 */
    @Schema(description = "收费项定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemDefinitionId;

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
