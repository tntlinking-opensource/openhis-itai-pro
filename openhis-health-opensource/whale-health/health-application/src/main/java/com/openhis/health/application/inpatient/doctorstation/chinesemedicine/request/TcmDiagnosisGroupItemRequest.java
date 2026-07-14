/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-32：中医病证组批次元素。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中医病证组批次元素")
public class TcmDiagnosisGroupItemRequest {

    /** 中医证候组号。 */
    @Schema(description = "中医证候组号")
    private String tcmSyndromeGroup;

    /** 病名目录主键。 */
    @NotNull(message = "病名目录不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "病名目录主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long diseaseDefinitionId;

    /** 病名病情事实主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "病名病情事实主键")
    private Long diseaseConditionId;

    /** 证候目录主键。 */
    @NotNull(message = "证候目录不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "证候目录主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long syndromeDefinitionId;

    /** 证候病情事实主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "证候病情事实主键")
    private Long syndromeConditionId;

    /** 组级主诊标记。 */
    @NotNull(message = "主诊标记不能为空")
    @Schema(description = "组级主诊标记", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer primaryFlag;

    /** 组级排序号。 */
    @NotNull(message = "排序号不能为空")
    @Schema(description = "组级排序号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rank;

    /** 医疗类型编码。 */
    @Schema(description = "医疗类型编码")
    private String medicalTypeCode;

    /** 组级诊断描述。 */
    @Schema(description = "组级诊断描述")
    private String diagnosisDesc;
}
