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
 * 医技诊疗划价写行（AS-MT-CHG-001 IF-MT-CHG-07）。
 *
 * <p>前端提交 activityId/quantity/unitCode；WriteAssembler 回填权威价目后再落库。
 * 生成来源固定 {@code medical-tech-pricing}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技诊疗划价写行")
public class MedicalTechServicePricingWriteItemDto {

    /** 诊疗活动定义主键。 */
    @Schema(description = "诊疗活动定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 数量（须 &gt; 0）。 */
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 单位码。 */
    @Schema(description = "单位码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

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
