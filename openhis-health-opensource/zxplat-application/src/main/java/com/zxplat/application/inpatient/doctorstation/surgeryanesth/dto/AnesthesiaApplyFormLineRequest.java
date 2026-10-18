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
 * 保存麻醉申请单行入参（AS-IP-DOR-012 IF-IP-SUR-12）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "麻醉申请单行")
public class AnesthesiaApplyFormLineRequest {

    /**
     * 行种类：开立仅允许 {@code ANESTHESIA} / {@code MEDICATION}。
     *
     * <p>{@code SURCHARGE} 为伪需求已废弃，Gate 拒绝；加收由护士划价。</p>
     */
    @Schema(description = "行种类；开立仅 ANESTHESIA/MEDICATION", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineKindCode;

    /** 计费诊疗项目主键（ANESTHESIA 必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "计费诊疗项目主键", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long activityDefinitionId;

    /** 药品定义主键（MEDICATION）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long medicationDefinitionId;

    /** 开立数量。 */
    @Schema(description = "开立数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 频次码（临时诊疗/药品必填；与医嘱开立同口径）。 */
    @Schema(description = "频次码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rateCode;

    /** 用法/给药途径（MEDICATION 必填）。 */
    @Schema(description = "用法码")
    private String methodCode;

    /** 单次剂量（MEDICATION 必填）。 */
    @Schema(description = "单次剂量")
    private BigDecimal dose;

    /**
     * 单次剂量单位（MEDICATION；术语 {@code dose_unit}）。
     *
     * <p>麻醉开立不按本单位换算总量，可选手动改选；空时写路径回落 {@code unitCode}。</p>
     */
    @Schema(description = "单次剂量单位（dose_unit）")
    private String doseUnitCode;

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNumber;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String note;
}
