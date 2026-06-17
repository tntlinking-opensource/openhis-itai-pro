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

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request;

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

    /** 中医证候组号；空表示新建组。 */
    @Schema(description = "中医证候组号")
    private String tcmSyndromeGroup;

    /** 病名目录主键。 */
    @NotNull(message = "病名目录不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "病名目录主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long diseaseDefinitionId;

    /** 病名病情事实主键（更新时）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "病名病情事实主键")
    private Long diseaseConditionId;

    /** 证候目录主键。 */
    @NotNull(message = "证候目录不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "证候目录主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long syndromeDefinitionId;

    /** 证候病情事实主键（更新时）。 */
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
