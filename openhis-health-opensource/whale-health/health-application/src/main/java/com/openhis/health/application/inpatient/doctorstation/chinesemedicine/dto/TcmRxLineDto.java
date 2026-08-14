/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-34：中药处方明细行展示 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方明细行")
public class TcmRxLineDto {

    /** 药品申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品申请主键")
    private Long medicationRequestId;

    /** 饮片药品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "饮片药品定义主键")
    private Long medicationDefinitionId;

    /** 饮片展示名称。 */
    @Schema(description = "饮片展示名称")
    private String medicationName;

    /** 单剂用量。 */
    @Schema(description = "单剂用量")
    private BigDecimal dose;

    /** 单剂用量单位编码。 */
    @Schema(description = "单剂用量单位编码")
    private String doseUnitCode;

    /** 单剂用量单位展示名。 */
    @Schema(description = "单剂用量单位展示名")
    private String doseUnitName;

    /** 请求数量。 */
    @Schema(description = "请求数量")
    private BigDecimal quantity;

    /** 数量单位编码。 */
    @Schema(description = "数量单位编码")
    private String unitCode;

    /** 数量单位展示名。 */
    @Schema(description = "数量单位展示名")
    private String unitName;

    /** 单剂用量单位零售价。 */
    @Schema(description = "单剂用量单位零售价")
    private BigDecimal retailUnitPrice;

    /** 味序。 */
    @Schema(description = "味序")
    private Integer sortNumber;

    /** 行状态枚举码。 */
    @Schema(description = "行状态枚举码")
    private String lineStatusEnum;

    /** 特殊煎法术语 code。 */
    @Schema(description = "特殊煎法术语 code")
    private String dosageInstructionCode;

    /** 特殊煎法展示名。 */
    @Schema(description = "特殊煎法展示名")
    private String dosageInstructionText;

    /** 行级费用性质/合同主键（本行 {@code med_medication_request.contract_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "行级费用性质/合同主键")
    private Long contractId;

    /** 行级费用性质展示文案（合同分类：自费/医保等）。 */
    @Schema(description = "行级费用性质展示文案")
    private String feeNatureText;
}
