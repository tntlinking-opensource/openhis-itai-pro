/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IF-IP-ARG-02：护理级别只读区（不可写入配置表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理级别只读信息")
public class NurseStationAutoRollNursingReadonlyDto {

    /** 护理医嘱主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护理医嘱主键")
    private Long serviceRequestId;

    /** 护理活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护理活动定义主键")
    private Long activityId;

    /** 护理级别名称。 */
    @Schema(description = "护理级别名称")
    private String activityName;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 单位名称。 */
    @Schema(description = "单位名称")
    private String unitName;

    /** 校对时刻。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "校对时刻")
    private LocalDateTime checkTime;

    /** 医嘱状态码。 */
    @Schema(description = "医嘱状态码")
    private String statusEnum;

    /** 医嘱状态展示名。 */
    @Schema(description = "医嘱状态展示名")
    private String statusDictText;

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

    /** 业务日。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "业务日")
    private LocalDate billDate;
}
