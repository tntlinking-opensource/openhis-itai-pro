/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 医技耗材划价写行（AS-MT-CHG-001 IF-MT-CHG-02）。
 *
 * <p>前端提交 definitionId/quantity/unitCode/locationId/lotNumber；
 * WriteAssembler 回填权威价目与金额字段后再落库。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技耗材划价写行")
public class MedicalTechDevicePricingWriteItemDto {

    /** 耗材定义主键。 */
    @Schema(description = "耗材定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long definitionId;

    /** 数量（须 &gt; 0）。 */
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 单位码。 */
    @Schema(description = "单位码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 耗材房主键（须属登录 TECH 下属）。 */
    @Schema(description = "耗材房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /**
     * 目录选中批号（可空）：有值时锁定 {@code LOT_NUMBER_PRICE}，且自动扣库按该批定批；
     * 空则 finalize 仍 FIFO。
     */
    @Schema(description = "批号")
    private String lotNumber;

    /** 患者主键（App 自就诊回填）。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 挂账账户主键（App 自账户查询回填）。 */
    @Schema(description = "挂账账户主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 收费项定义主键（WriteAssembler 权威定价回填）。 */
    @Schema(description = "收费项定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemDefinitionId;

    /** 权威单价（WriteAssembler 回填）。 */
    @Schema(description = "权威单价")
    private BigDecimal unitPrice;

    /** 行金额（WriteAssembler 回填）。 */
    @Schema(description = "行金额")
    private BigDecimal totalPrice;
}
