/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmRxDraftLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-DOR-36：中药处方暂存方头请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方暂存方头")
public class TcmRxDraftHeaderRequest {

    /** 前端方标识。 */
    @NotBlank(message = "prescriptionGroupKey 不能为空")
    @Schema(description = "前端方标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prescriptionGroupKey;

    /** 绑定的中医病证组号。 */
    @NotBlank(message = "中医病证组号不能为空")
    @Schema(description = "绑定的中医病证组号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tcmSyndromeGroup;

    /**
     * 方头绑定就诊诊断有序 ids 串（逗号分隔；可多选，与西医开嘱 {@code encounterDiagnosisIds} 同口径）。
     * <p>非空时优先写入药品申请；空则回落为 {@link #tcmSyndromeGroup} 对应病+证 ids。</p>
     */
    @Schema(description = "方头绑定就诊诊断有序 ids 串")
    private String encounterDiagnosisIds;

    /** 中药付数。 */
    @NotNull(message = "付数不能为空")
    @Schema(description = "中药付数", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal chineseHerbsDoseQuantity;

    /** 代煎标志。 */
    @Schema(description = "代煎标志")
    private Boolean sufferingFlag;

    /** 用法编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用药频次编码。 */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /** 用药天数。 */
    @Schema(description = "用药天数")
    private Integer days;

    /** 煎煮法编码。 */
    @Schema(description = "煎煮法编码")
    private String decoctionMethodCode;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /**
     * 方头默认费用性质/合同主键（行未传 {@code contractId} 时回落写入各味；空则挂账走就诊默认账户）。
     * <p>以行为主：明细行可各自传合同，自费等级药材不得因方头覆盖拖整方。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质/合同主键（方头默认，行可覆盖）")
    private Long contractId;

    /** 方内明细行。 */
    @NotEmpty(message = "处方明细不能为空")
    @Valid
    @Schema(description = "方内明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TcmRxDraftLineRequest> lines;
}
