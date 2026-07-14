/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadFinTypeRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadRegistrationRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadSummaryTotalsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室/医生工作量汇总查询响应（IF-RPT-WKL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室医生工作量汇总查询响应")
public class QueryPractitionerWorkloadSummaryResponse {

    /** 就诊范围。 */
    @Schema(description = "就诊范围")
    private String encounterScope;

    /** 科室 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室 id")
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** fin_type 汇总行。 */
    @Schema(description = "fin_type 汇总行")
    private List<PractitionerWorkloadFinTypeRowDto> finTypeRows;

    /** 挂号号源汇总行。 */
    @Schema(description = "挂号号源汇总行")
    private List<PractitionerWorkloadRegistrationRowDto> registrationRows;

    /** 汇总合计。 */
    @Schema(description = "汇总合计")
    private PractitionerWorkloadSummaryTotalsDto summaryTotals;
}
