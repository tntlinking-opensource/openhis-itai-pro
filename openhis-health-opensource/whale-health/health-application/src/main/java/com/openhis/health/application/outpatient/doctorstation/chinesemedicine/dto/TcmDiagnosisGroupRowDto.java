/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-30：中医病证组展示行（一病一证成组）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中医病证组展示行")
public class TcmDiagnosisGroupRowDto {

    /** 中医证候组号。 */
    @Schema(description = "中医证候组号")
    private String tcmSyndromeGroup;

    /** 病名病情事实主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "病名病情事实主键")
    private Long diseaseConditionId;

    /** 病名目录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "病名目录主键")
    private Long diseaseDefinitionId;

    /** 病名编码。 */
    @Schema(description = "病名编码")
    private String diseaseCode;

    /** 病名展示名称。 */
    @Schema(description = "病名展示名称")
    private String diseaseName;

    /** 证候病情事实主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "证候病情事实主键")
    private Long syndromeConditionId;

    /** 证候目录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "证候目录主键")
    private Long syndromeDefinitionId;

    /** 证候编码。 */
    @Schema(description = "证候编码")
    private String syndromeCode;

    /** 证候展示名称。 */
    @Schema(description = "证候展示名称")
    private String syndromeName;

    /** 组级主诊标记。 */
    @Schema(description = "组级主诊标记")
    private Integer primaryFlag;

    /** 组级排序号。 */
    @Schema(description = "组级排序号")
    private Integer rank;

    /** 医疗类型编码。 */
    @Schema(description = "医疗类型编码")
    private String medicalTypeCode;

    /** 组级诊断描述。 */
    @Schema(description = "组级诊断描述")
    private String diagnosisDesc;
}
