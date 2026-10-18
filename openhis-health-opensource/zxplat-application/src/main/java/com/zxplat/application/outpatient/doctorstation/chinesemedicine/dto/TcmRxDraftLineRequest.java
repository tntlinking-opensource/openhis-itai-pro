/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-36：中药处方暂存明细行请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方暂存明细行")
public class TcmRxDraftLineRequest {

    /** 行操作。 */
    @NotBlank(message = "行操作不能为空")
    @Schema(description = "行操作", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineOp;

    /** 药品申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品申请主键")
    private Long medicationRequestId;

    /** 饮片药品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "饮片药品定义主键")
    private Long medicationDefinitionId;

    /** 单剂用量。 */
    @Schema(description = "单剂用量")
    private BigDecimal dose;

    /** 单剂用量单位编码。 */
    @Schema(description = "单剂用量单位编码")
    private String doseUnitCode;

    /** 味序。 */
    @Schema(description = "味序")
    private Integer sortNumber;

    /** 发药库位主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药库位主键")
    private Long dispenseLocationId;

    /** 特殊煎法术语 code。 */
    @Schema(description = "特殊煎法术语 code")
    private String dosageInstructionCode;

    /**
     * 行级费用性质/合同主键（落库本行 {@code med_medication_request.contract_id}）。
     * <p>优先于方头 {@code contractId}；自费等级药材仅本行切自费，不拖整方。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "行级费用性质/合同主键")
    private Long contractId;
}
