/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto;

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

    /** 中医证候组号（库列 tcm_syndrome_group）。 */
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

    /** 组级主诊标记：1 主诊 / 0 非主诊。 */
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
