/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * IF-IP-ARG-02：自动计费配置列表行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费配置行")
public class NurseStationAutoRollConfigRowDto {

    /** 规则主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "规则主键")
    private Long ruleId;

    /** 规则名称。 */
    @Schema(description = "规则名称")
    private String ruleName;

    /** 状态码。 */
    @Schema(description = "状态码")
    private String statusEnum;

    /** 状态展示名。 */
    @Schema(description = "状态展示名")
    private String statusDictText;

    /** 住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键")
    private Long encounterId;

    /** 触发来源表。 */
    @Schema(description = "触发来源表")
    private String triggerSourceTable;

    /** 床位服务活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "床位服务活动定义主键")
    private Long triggerSourceId;

    /** 床位服务名称。 */
    @Schema(description = "床位服务名称")
    private String activityName;

    /** 收费项定价主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定价主键")
    private Long chargeDefinitionId;

    /** 定价名称。 */
    @Schema(description = "定价名称")
    private String chargeDefinitionName;

    /** 解析单价（展示参考；滚费落账以写路径重算为准）。 */
    @Schema(description = "解析单价")
    private BigDecimal unitPrice;

    /** 生成数量。 */
    @Schema(description = "生成数量")
    private BigDecimal quantityValue;

    /** 执行科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室主键")
    private Long performingOrgId;

    /** 优先级。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /**
     * 滚费业务时刻（billDate 当日）。
     */
    @Schema(description = "滚费业务时刻")
    private LocalTime businessTime;

    /**
     * 滚费计费人主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "滚费计费人主键")
    private Long billingPractitionerId;

    /**
     * 滚费计费人姓名。
     */
    @Schema(description = "滚费计费人姓名")
    private String billingPractitionerName;
}
