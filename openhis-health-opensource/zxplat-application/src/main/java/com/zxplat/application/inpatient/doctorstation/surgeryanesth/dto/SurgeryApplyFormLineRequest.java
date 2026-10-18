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
 * 保存手术申请单行入参（AS-IP-DOR-012 IF-IP-SUR-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "手术申请单行")
public class SurgeryApplyFormLineRequest {

    /**
     * 行种类：开立仅允许 {@code PROCEDURE} / {@code ENTRUST}。
     *
     * <p>{@code SURCHARGE} 已废弃；加收由护士划价。嘱托须 {@code basedOnProcedureSortNumber} 挂靠术式。</p>
     */
    @Schema(description = "行种类；开立仅 PROCEDURE/ENTRUST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineKindCode;

    /** 国临手术定义主键（术式行必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "国临手术定义主键（术式行必填）")
    private Long procedureDefinitionId;

    /**
     * 计费诊疗项目主键（术式/嘱托行忽略；嘱托后端解析内置「嘱托」AD）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "计费诊疗项目主键（术式/嘱托可空）")
    private Long activityDefinitionId;

    /**
     * 术前嘱托正文（{@code ENTRUST} 必填；落库 {@code entrust_content}）。
     */
    @Schema(description = "术前嘱托正文（ENTRUST 必填）")
    private String entrustContent;

    /**
     * 挂靠术式行的排序号（{@code ENTRUST} 必填；对应本请求中某条 PROCEDURE 的 {@link #sortNumber}）。
     *
     * <p>落库 basedOn = 该术式 SR；手麻开立唯一 basedOn 主子。</p>
     */
    @Schema(description = "嘱托挂靠术式的 sortNumber")
    private Integer basedOnProcedureSortNumber;

    /** 开立数量。 */
    @Schema(description = "开立数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /**
     * 频次码。
     *
     * <p>术式行可空（后端默认「立即」）；嘱托可空（默认立即）。</p>
     */
    @Schema(description = "频次码；术式/嘱托可空默认立即")
    private String rateCode;

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNumber;

    /** 主手术标志。 */
    @Schema(description = "主手术标志")
    private Boolean mainProcedureFlag;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String note;
}
