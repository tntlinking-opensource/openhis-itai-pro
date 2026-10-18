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
 * 手术申请单行（AS-IP-DOR-012 IF-IP-SUR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "手术申请单行")
public class SurgeryApplyFormLineDto {

    /** 服务申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务申请主键")
    private Long serviceRequestId;

    /**
     * 行种类：PROCEDURE / ENTRUST；历史/划价可能回显 SURCHARGE。
     */
    @Schema(description = "行种类 PROCEDURE/ENTRUST；历史可回显 SURCHARGE")
    private String lineKindCode;

    /** 国临手术定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "国临手术定义主键")
    private Long procedureDefinitionId;

    /** 国临手术编码。 */
    @Schema(description = "国临手术编码")
    private String procedureCode;

    /** 国临手术名称。 */
    @Schema(description = "国临手术名称")
    private String procedureName;

    /** 计费诊疗项目主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "计费诊疗项目主键")
    private Long activityDefinitionId;

    /** 诊疗项目名称。 */
    @Schema(description = "诊疗项目名称")
    private String activityName;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 行状态。 */
    @Schema(description = "行状态")
    private String statusEnum;

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNumber;

    /** 主手术标志。 */
    @Schema(description = "主手术标志")
    private Boolean mainProcedureFlag;

    /**
     * 术前嘱托正文（{@code ENTRUST} 行回显）。
     */
    @Schema(description = "术前嘱托正文")
    private String entrustContent;

    /**
     * 挂靠术式 SR 主键（{@code ENTRUST} 的 basedOnId）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂靠术式服务申请主键")
    private Long basedOnProcedureServiceRequestId;

    /**
     * 挂靠术式在本单的排序号（编辑回填用）。
     */
    @Schema(description = "挂靠术式 sortNumber")
    private Integer basedOnProcedureSortNumber;
}
