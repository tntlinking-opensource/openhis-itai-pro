/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加收诊疗项目检索项（AS-IP-DOR-012 IF-IP-SUR-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加收诊疗项目")
public class SurchargeActivityDefinitionItemDto {

    /** 诊疗项目主键（MAIN；麻醉主项选品）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗项目主键")
    private Long activityDefinitionId;

    /** 药品定义主键（MEDICATION）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键")
    private Long medicationDefinitionId;

    /** 项目编码。 */
    @Schema(description = "项目编码")
    private String busNo;

    /** 名称。 */
    @Schema(description = "名称")
    private String name;

    /** 类目编码。 */
    @Schema(description = "类目编码")
    private String categoryCode;

    /** 默认单位编码。 */
    @Schema(description = "默认单位编码")
    private String unitCode;

    /** 选品种类回显 MAIN/MEDICATION（EXTRA 已废弃）。 */
    @Schema(description = "选品种类")
    private String itemKindCode;
}
