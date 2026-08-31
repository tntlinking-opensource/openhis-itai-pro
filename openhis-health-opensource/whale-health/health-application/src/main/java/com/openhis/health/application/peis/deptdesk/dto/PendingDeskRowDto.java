/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 分科诊台待检队列行 DTO。
 */
@Data
public class PendingDeskRowDto {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 体检流水号。 */
    @Schema(description = "体检流水号")
    private String busNo;

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 诊疗活动定义主键。 */
    @Schema(description = "诊疗活动主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 项目展示名。 */
    @Schema(description = "项目展示名")
    private String activityDisplay;

    /** 执行状态 code。 */
    @Schema(description = "执行状态")
    private String execStatusEnum;

    /** LIS 外发状态 code。 */
    @Schema(description = "LIS 外发状态")
    private String lisDispatchEnum;

    /** 患者年龄。 */
    @Schema(description = "患者年龄")
    private Integer patientAge;

    /** 是否团检。 */
    @Schema(description = "是否团检")
    private Boolean groupFlag;

    /** 就诊检查状态 code。 */
    @Schema(description = "就诊检查状态")
    private String encounterStatusEnum;
}
