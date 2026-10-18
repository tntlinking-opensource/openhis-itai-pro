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
 * 国临手术定义检索项（AS-IP-DOR-012 IF-IP-SUR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "国临手术定义检索项")
public class ClinicalProcV3SearchItemDto {

    /** 手术定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "手术定义主键")
    private Long id;

    /** 国临手术编码。 */
    @Schema(description = "国临手术编码")
    private String procedureCode;

    /** 名称。 */
    @Schema(description = "名称")
    private String name;

    /** 拼音码。 */
    @Schema(description = "拼音码")
    private String pyStr;

    /** 五笔码。 */
    @Schema(description = "五笔码")
    private String wbStr;

    /** 医保手术码（可空）。 */
    @Schema(description = "医保手术码")
    private String ybNo;

    /** 发布状态。 */
    @Schema(description = "发布状态")
    private String statusEnum;

    /** 挂靠诊疗项目主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂靠诊疗项目主键")
    private Long activityDefinitionId;

    /** 挂靠诊疗项目名称。 */
    @Schema(description = "挂靠诊疗项目名称")
    private String activityName;
}
