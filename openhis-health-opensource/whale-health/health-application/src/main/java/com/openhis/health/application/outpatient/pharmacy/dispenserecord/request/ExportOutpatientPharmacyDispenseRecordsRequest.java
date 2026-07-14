/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispenserecord.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 导出门诊发退药记录请求（IF-OPR-03）；筛选字段与列表查询一致，不含分页键。
 */
@Data
@Accessors(chain = true)
@Schema(description = "导出门诊发退药记录请求")
public class ExportOutpatientPharmacyDispenseRecordsRequest {

    /** 发药时间起（yyyy-MM-dd）。 */
    @Schema(description = "发药时间起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateFrom;

    /** 发药时间止（yyyy-MM-dd）。 */
    @Schema(description = "发药时间止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateTo;

    /** 记录类型筛选项 code。 */
    @Schema(description = "记录类型筛选项 code")
    private String recordTypeCode;

    /** 药房库位主键。 */
    @Schema(description = "药房库位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pharmacyLocationId;

    /** 项目类型多选。 */
    @Schema(description = "项目类型 code 多选")
    private List<String> medicationCategoryCodes;

    /** 发药人主键。 */
    @Schema(description = "发药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenserPractitionerId;

    /** 药品关键字。 */
    @Schema(description = "药品关键字")
    private String drugKeyword;

    /** 患者关键字。 */
    @Schema(description = "患者关键字")
    private String patientKeyword;
}
