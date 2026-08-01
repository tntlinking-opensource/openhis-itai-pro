/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊挂号明细报表净汇总行（IF-RPT-ORG-02/04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表汇总行")
public class OutpatientRegistrationDetailSummaryRowDto {

    /** 分桶键（科室或医生 × 号源）。 */
    @Schema(description = "分桶键")
    private String dimensionKey;

    /** 汇总视角码（ORGANIZATION/DOCTOR）。 */
    @Schema(description = "汇总视角码")
    private String summaryDimension;

    /** 挂号科室主键；医生维时可空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号科室主键")
    private Long organizationId;

    /** 挂号科室名称。 */
    @Excel(name = "科室", sort = 1)
    @Schema(description = "挂号科室名称")
    private String organizationName;

    /** 挂号医生主键；科室维时为 null。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号医生主键")
    private Long practitionerId;

    /** 挂号医生姓名。 */
    @Excel(name = "医生", sort = 2)
    @Schema(description = "挂号医生姓名")
    private String practitionerName;

    /** 号源类型主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "号源类型主键")
    private Long serviceTypeId;

    /** 号源类型名称（便民/普通/专家等）。 */
    @Excel(name = "号源类型", sort = 3)
    @Schema(description = "号源类型名称")
    private String serviceTypeName;

    /** 净挂号笔数（退号冲减后）。 */
    @Excel(name = "净挂号笔数", sort = 4)
    @Schema(description = "净挂号笔数")
    private Long netRegistrationCount;
}

