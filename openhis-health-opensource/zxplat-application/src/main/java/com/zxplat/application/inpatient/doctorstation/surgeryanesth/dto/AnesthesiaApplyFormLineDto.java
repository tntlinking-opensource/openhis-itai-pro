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

import java.math.BigDecimal;

/**
 * 麻醉申请单行（AS-IP-DOR-012 IF-IP-SUR-11）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "麻醉申请单行")
public class AnesthesiaApplyFormLineDto {

    /** 服务申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务申请主键")
    private Long serviceRequestId;

    /**
     * 行种类：ANESTHESIA / MEDICATION；历史可回显 SURCHARGE。
     */
    @Schema(description = "行种类 ANESTHESIA/MEDICATION")
    private String lineKindCode;

    /** 计费诊疗项目主键（麻醉项目）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "计费诊疗项目主键")
    private Long activityDefinitionId;

    /** 诊疗项目名称。 */
    @Schema(description = "诊疗项目名称")
    private String activityName;

    /** 药品定义主键（麻醉药品）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键")
    private Long medicationDefinitionId;

    /** 药品名称（回显）。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 药品请求主键（MEDICATION 行）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品请求主键")
    private Long medicationRequestId;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 单位展示名（术语 unit_code 文案；禁止前端把码当名）。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 频次码。 */
    @Schema(description = "频次码")
    private String rateCode;

    /** 用法码（药品）。 */
    @Schema(description = "用法码")
    private String methodCode;

    /** 单次剂量（药品）。 */
    @Schema(description = "单次剂量")
    private BigDecimal dose;

    /**
     * 单次剂量单位码（药品；术语 {@code dose_unit}）。
     *
     * <p>麻醉开立不按本单位换算总量。</p>
     */
    @Schema(description = "单次剂量单位码（dose_unit）")
    private String doseUnitCode;

    /** 单次剂量单位展示名（术语 dose_unit 文案）。 */
    @Schema(description = "单次剂量单位展示名")
    private String doseUnitName;

    /** 行状态。 */
    @Schema(description = "行状态")
    private String statusEnum;

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNumber;
}
