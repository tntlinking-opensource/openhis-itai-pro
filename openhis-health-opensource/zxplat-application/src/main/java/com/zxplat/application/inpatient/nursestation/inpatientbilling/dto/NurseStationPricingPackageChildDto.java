/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗套餐子项（IF-IP-PRC-01 选品回传；对齐 {@code wkf_activity_definition_children}）。
 */
@Getter
@Setter
/** 诊疗套餐子项。 */
@Schema(description = "诊疗套餐子项")
public class NurseStationPricingPackageChildDto {

    @Schema(description = "子项诊疗项目 id")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 子项诊疗项目 id。 */
    private Long childrenId;

    /** 子项数量系数。 */
    @Schema(description = "子项数量系数")
    private Integer childrenNum;

    /** 子项名称。 */
    @Schema(description = "子项名称")
    private String childrenName;

    /** 子项单位 code。 */
    @Schema(description = "子项单位 code")
    private String unitCode;

    /** 子项数量。 */
    @Schema(description = "子项数量")
    private BigDecimal quantity;
}
