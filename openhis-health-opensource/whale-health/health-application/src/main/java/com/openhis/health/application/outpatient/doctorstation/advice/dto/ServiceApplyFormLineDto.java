/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-DOR-22 / IF-IP-DOR-24：申请单明细行摘要。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单明细行摘要")
public class ServiceApplyFormLineDto {

    /** 服务申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务申请主键")
    private Long requestId;

    /** 诊疗活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 诊疗名称。 */
    @Schema(description = "诊疗名称")
    private String activityName;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 纯备注。 */
    @Schema(description = "纯备注")
    private String remark;

    /** 医嘱优先级 code（加急=EM）。 */
    @Schema(description = "医嘱优先级 code（加急=EM）")
    private String priorityEnum;

    /** 部位/样本明细。 */
    @Schema(description = "部位/样本明细")
    private List<ServiceRequestDetailSummaryDto> details;
}
