/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-22 / IF-IP-DOR-24：申请单头摘要（按 {@code prescriptionNo} 分组）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单头摘要")
public class ServiceApplyFormHeaderDto {

    /** 申请单号（AR 前缀）。 */
    @Schema(description = "申请单号（AR 前缀）")
    private String prescriptionNo;

    /** 申请单类别 code。 */
    @Schema(description = "申请单类别 code")
    private String categoryEnum;

    /** 整单执行科室。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "整单执行科室")
    private Long orgId;

    /** 执行科室展示名。 */
    @Schema(description = "执行科室展示名")
    private String orgName;

    /** 首行开立时间 ISO-8601 local（{@code req_opened_time}）。 */
    @Schema(description = "首行开立时间 ISO-8601 local（req_opened_time）")
    private String reqOpenedTime;

    /** 组内状态摘要。 */
    @Schema(description = "组内状态摘要")
    private String statusSummary;

    /** 明细行。 */
    @Schema(description = "明细行")
    private List<ServiceApplyFormLineDto> lines;
}
